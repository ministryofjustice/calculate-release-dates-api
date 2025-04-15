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
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RecallType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.UpdateOffenderDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationOutcomeRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationReasonRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationRequestRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationType.MANUAL_ENTRY_JOURNEY_REQUIRED
import java.time.LocalDate
import java.time.Period

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
  private val sentenceIdentificationService: SentenceIdentificationService,
  private val sentenceCombinationService: SentenceCombinationService,
  private val validationService: ValidationService,
  private val calculationSourceDataService: CalculationSourceDataService,
  private val calculationService: CalculationService,
) {

  fun hasIndeterminateSentences(bookingId: Long): Boolean {
    val sentencesAndOffences = prisonService.getSentencesAndOffences(bookingId)
    return sentencesAndOffences.any { SentenceCalculationType.isIndeterminate(it.sentenceCalculationType) }
  }

  fun hasRecallSentences(bookingId: Long): Boolean {
    val sentencesAndOffences = prisonService.getSentencesAndOffences(bookingId)
    return sentencesAndOffences.any { SentenceCalculationType.from((it.sentenceCalculationType)).recallType != null }
  }

  // Write a method to create EffectiveSentenceLength
  @Transactional
  fun storeManualCalculation(
    prisonerId: String,
    manualEntryRequest: ManualEntryRequest,
    isGenuineOverride: Boolean = false,
  ): ManualCalculationResponse {
    val sourceData = calculationSourceDataService.getCalculationSourceData(prisonerId, InactiveDataOptions.default())
    val calculationUserInputs = CalculationUserInputs()
    val booking = bookingService.getBooking(sourceData, calculationUserInputs)

    val effectiveSentenceLength = try {
      calculateEffectiveSentenceLength(booking, manualEntryRequest)
    } catch (ex: Exception) {
      log.info("Exception caught calculating ESL for $prisonerId, setting to zero.")
      Period.ZERO
    }

    val reasonForCalculation = calculationReasonRepository.findById(manualEntryRequest.reasonForCalculationId)
      .orElse(null) // TODO: This should thrown an EntityNotFoundException when the reason is mandatory.
    val type =
      if (isGenuineOverride) {
        CalculationType.MANUAL_OVERRIDE
      } else if (hasIndeterminateSentences(booking.bookingId)) {
        CalculationType.MANUAL_INDETERMINATE
      } else {
        CalculationType.MANUAL_DETERMINATE
      }

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

      val preCalcManualJourneyErrors = validationService.validateSupportedSentencesAndCalculations(sourceData)

      if (preCalcManualJourneyErrors.isNotEmpty()) {
        savedCalculationRequest.manualCalculationReason = preCalcManualJourneyErrors.map { transform(savedCalculationRequest, it) }
        calculationRequestRepository.save(savedCalculationRequest)
      } else {
        val calculationOutput = calculationService.calculateReleaseDates(
          booking,
          calculationUserInputs,
        )

        val postCalcManualJourneyErrors = validationService
          .validateBookingAfterCalculation(calculationOutput, booking)
          .filter { it.type == MANUAL_ENTRY_JOURNEY_REQUIRED }

        if (postCalcManualJourneyErrors.isNotEmpty()) {
          savedCalculationRequest.manualCalculationReason = postCalcManualJourneyErrors.map { transform(savedCalculationRequest, it) }
          calculationRequestRepository.save(savedCalculationRequest)
        }
      }

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
    val hasFixedTermRecallSentences = booking.sentences.filter { it.recallType == RecallType.FIXED_TERM_RECALL_14 || it.recallType == RecallType.FIXED_TERM_RECALL_28 }
    if (hasIndeterminateSentences(booking.bookingId) ||
      (hasFixedTermRecallSentences.isNotEmpty() && booking.returnToCustodyDate == null)
    ) {
      return Period.ZERO
    } else {
      booking.sentences.forEach {
        sentenceIdentificationService.identify(it, booking.offender)
      }
      val sentences = sentenceCombinationService.getSentencesToCalculate(booking.sentences, booking.offender)
      val earliestSentenceDate = sentences.minOfOrNull { it.sentencedAt }
      val sed = getSED(manualEntryRequest)
      return if (sed != null && earliestSentenceDate != null) {
        val period = Period.between(earliestSentenceDate, sed)
        if (!period.isNegative) period else Period.ZERO
      } else {
        Period.ZERO
      }
    }
  }

  fun getSED(manualEntryRequest: ManualEntryRequest): LocalDate? {
    val sed = manualEntryRequest.selectedManualEntryDates.find { it.dateType == ReleaseDateType.SED }
    return sed?.date?.toLocalDate()
  }

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}
