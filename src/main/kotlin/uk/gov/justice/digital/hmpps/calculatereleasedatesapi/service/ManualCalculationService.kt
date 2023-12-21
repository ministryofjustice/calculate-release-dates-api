package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.persistence.EntityNotFoundException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
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
) {

  fun hasIndeterminateSentences(bookingId: Long): Boolean {
    val sentencesAndOffences = prisonService.getSentencesAndOffences(bookingId)
    return sentencesAndOffences.any { SentenceCalculationType.isIndeterminate(it.sentenceCalculationType) }
  }

  @Transactional
  fun storeManualCalculation(
    prisonerId: String,
    manualEntryRequest: ManualEntryRequest,
    comment: String? = null,
  ): ManualCalculationResponse {
    val sourceData = prisonService.getPrisonApiSourceData(prisonerId, true)
    val booking = bookingService.getBooking(sourceData, CalculationUserInputs())
    val type =
      if (comment != null) CalculationType.MANUAL_OVERRIDE else if (hasIndeterminateSentences(booking.bookingId)) CalculationType.MANUAL_INDETERMINATE else CalculationType.MANUAL_DETERMINATE
    val reasonForCalculation = calculationReasonRepository.findById(manualEntryRequest.reasonForCalculationId).orElse(null) //TODO: This should thrown an EntityNotFoundException when the reason is mandatory.

    val calculationRequest = transform(
      booking,
      serviceUserService.getUsername(),
      CalculationStatus.CONFIRMED,
      sourceData,
      reasonForCalculation,
      objectMapper,
      manualEntryRequest.otherReasonDescription,
    ).withType(CalculationType.CALCULATED)

    return try {
      val savedCalculationRequest = calculationRequestRepository.save(calculationRequest)
      val calculationOutcomes =
        manualEntryRequest.selectedManualEntryDates.map { transform(savedCalculationRequest, it) }
      calculationOutcomeRepository.saveAll(calculationOutcomes)
      val enteredDates =
        writeToNomisAndPublishEvent(prisonerId, booking, savedCalculationRequest.id, calculationOutcomes, comment)
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
    comment: String? = null,
  ): Map<ReleaseDateType, LocalDate?>? {
    val calculationRequest = calculationRequestRepository.findById(calculationRequestId)
      .orElseThrow { EntityNotFoundException("No calculation request exists") }
    val dates = calculationOutcomes.map { ReleaseDateType.valueOf(it.calculationDateType) to it.outcomeDate }.toMap()
    val commentToSave =
      comment ?: if (dates.containsKey(ReleaseDateType.None)) INDETERMINATE_COMMENT else DETERMINATE_COMMENT
    val updateOffenderDates = UpdateOffenderDates(
      calculationUuid = calculationRequest.calculationReference,
      submissionUser = serviceUserService.getUsername(),
      keyDates = transform(dates),
      noDates = dates.containsKey(ReleaseDateType.None),
      comment = commentToSave.format(calculationRequest.calculationReference),
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

  private companion object {
    const val INDETERMINATE_COMMENT =
      "An Indeterminate (Life) sentence was entered with no dates currently available. This was intentionally recorded as blank. It was entered using the Calculate release dates service. The calculation ID is: %s"
    const val DETERMINATE_COMMENT =
      "The information shown was manually recorded in the Calculate release dates service. The calculation ID is: %s"
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}
