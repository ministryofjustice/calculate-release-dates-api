package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.persistence.EntityNotFoundException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.info.BuildProperties
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationOutcome
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationStatus
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.CouldNotSaveManualEntryException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationUserInputs
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ManualCalculationResponse
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ManualEntryRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.UpdateOffenderDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationOutcomeRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationReasonRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationRequestRepository
import java.time.LocalDate
import java.time.Period
import java.util.*

@Service
class ManualCalculationService(
  private val prisonService: PrisonService,
  private val bookingService: BookingService,
  private val calculationOutcomeRepository: CalculationOutcomeRepository,
  private val calculationRequestRepository: CalculationRequestRepository,
  private val calculationReasonRepository: CalculationReasonRepository,
  private val objectMapper: ObjectMapper,
  private val eventService: EventService,
  private val serviceUserService: ServiceUserService,
  private val nomisCommentService: NomisCommentService,
  private val buildProperties: BuildProperties,
  private val bookingCalculationService: BookingCalculationService,
) {

  fun hasIndeterminateSentences(bookingId: Long): Boolean {
    val sentencesAndOffences = prisonService.getSentencesAndOffences(bookingId)
    return sentencesAndOffences.any { SentenceCalculationType.isIndeterminate(it.sentenceCalculationType) }
  }

  // Write a method to create EffectiveSentenceLength
  @Transactional
  fun storeManualCalculation(
    prisonerId: String,
    manualEntryRequest: ManualEntryRequest,
    isGenuineOverride: Boolean? = false,
  ): ManualCalculationResponse {
    val sourceData = prisonService.getPrisonApiSourceData(prisonerId, true)
    var booking = bookingService.getBooking(sourceData, CalculationUserInputs())

    val effectiveSentenceLength = calculateEffectiveSentenceLength(booking, manualEntryRequest)

    val reasonForCalculation = calculationReasonRepository.findById(manualEntryRequest.reasonForCalculationId)
      .orElse(null) // TODO: This should thrown an EntityNotFoundException when the reason is mandatory.
    val type =
      if (isGenuineOverride!!) CalculationType.MANUAL_OVERRIDE else if (hasIndeterminateSentences(booking.bookingId)) CalculationType.MANUAL_INDETERMINATE else CalculationType.MANUAL_DETERMINATE

    val calculationRequest = transform(
      booking,
      serviceUserService.getUsername(),
      CalculationStatus.CONFIRMED,
      sourceData,
      reasonForCalculation,
      objectMapper,
      manualEntryRequest.otherReasonDescription,
      version = buildProperties.version,
    ).withType(type)

    return try {
      val savedCalculationRequest = calculationRequestRepository.save(calculationRequest)
      val calculationOutcomes =
        manualEntryRequest.selectedManualEntryDates.map { transform(savedCalculationRequest, it) }
      calculationOutcomeRepository.saveAll(calculationOutcomes)
      val enteredDates =
        writeToNomisAndPublishEvent(
          prisonerId,
          booking,
          savedCalculationRequest.id,
          calculationOutcomes,
          isGenuineOverride,
          effectiveSentenceLength,
        )
          ?: throw CouldNotSaveManualEntryException("There was a problem saving the dates")
      ManualCalculationResponse(enteredDates, savedCalculationRequest.id)
    } catch (ex: Exception) {
      calculationRequestRepository.save(
        transform(
          booking,
          serviceUserService.getUsername(),
          CalculationStatus.ERROR,
          sourceData,
          reasonForCalculation,
          objectMapper,
          manualEntryRequest.otherReasonDescription,
          version = buildProperties.version,
        ),
      )
      ManualCalculationResponse(emptyMap(), calculationRequest.id)
    }
  }

  @Transactional(readOnly = true)
  fun writeToNomisAndPublishEvent(
    prisonerId: String,
    booking: Booking,
    calculationRequestId: Long,
    calculationOutcomes: List<CalculationOutcome>,
    isGenuineOverride: Boolean,
    effectiveSentenceLength: Period,
  ): Map<ReleaseDateType, LocalDate?>? {
    val calculationRequest = calculationRequestRepository.findById(calculationRequestId)
      .orElseThrow { EntityNotFoundException("No calculation request exists") }
    val dates = calculationOutcomes.map { ReleaseDateType.valueOf(it.calculationDateType) to it.outcomeDate }.toMap()
    val updateOffenderDates = UpdateOffenderDates(
      calculationUuid = calculationRequest.calculationReference,
      submissionUser = serviceUserService.getUsername(),
      keyDates = transform(dates, effectiveSentenceLength),
      noDates = dates.containsKey(ReleaseDateType.None),
      reason = calculationRequest.reasonForCalculation?.nomisReason,
      comment = nomisCommentService.getManualNomisComment(calculationRequest, dates, isGenuineOverride),
    )
    try {
      prisonService.postReleaseDates(booking.bookingId, updateOffenderDates)
    } catch (ex: Exception) {
      CalculationTransactionalService.log.error("Nomis write failed: ${ex.message}")
      throw EntityNotFoundException(
        "Writing release dates to NOMIS failed for prisonerId $prisonerId " +
          "and bookingId ${booking.bookingId}",
      )
    }
    runCatching {
      eventService.publishReleaseDatesChangedEvent(prisonerId, booking.bookingId)
    }.onFailure { error ->
      log.error(
        "Failed to send release-dates-changed-event for prisoner ID $prisonerId",
        error,
      )
    }
    return dates
  }

  fun calculateEffectiveSentenceLength(booking: Booking, manualEntryRequest: ManualEntryRequest): Period {
    if (hasIndeterminateSentences(booking.bookingId)) {
      return Period.of(0, 0, 0)
    } else {
      val identifiedBooking = bookingCalculationService.identify(booking)
      val consecutiveSentencesBooking = bookingCalculationService.createConsecutiveSentences(identifiedBooking)
      val sentences = consecutiveSentencesBooking.getAllExtractableSentences()
      val earliestSentenceDate = sentences.minOf { it.sentencedAt }
      val sedOptional = getSED(manualEntryRequest)
      if (sedOptional.isPresent) {
        val sed = sedOptional.get()
        return Period.between(earliestSentenceDate, sed)
      } else {
        return Period.of(0, 0, 0)
      }
    }
  }

  fun getSED(manualEntryRequest: ManualEntryRequest): Optional<LocalDate> {
    val sed = manualEntryRequest.selectedManualEntryDates.find { it.dateType == ReleaseDateType.SED }
    return if (sed != null) {
      Optional.ofNullable(sed.date?.toLocalDate())
    } else {
      Optional.empty()
    }
  }

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}
