package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.persistence.EntityNotFoundException
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
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ManualEntrySelectedDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.UpdateOffenderDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationOutcomeRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationRequestRepository
import java.time.LocalDate

@Service
class ManualCalculationService(
  private val prisonService: PrisonService,
  private val bookingService: BookingService,
  private val calculationOutcomeRepository: CalculationOutcomeRepository,
  private val calculationRequestRepository: CalculationRequestRepository,
  private val objectMapper: ObjectMapper,
  private val eventService: EventService,
  private val serviceUserService: ServiceUserService,
) {

  fun hasIndeterminateSentences(bookingId: Long): Boolean {
    val sentencesAndOffences = prisonService.getSentencesAndOffences(bookingId)
    return sentencesAndOffences.any { SentenceCalculationType.isIndeterminate(it.sentenceCalculationType) }
  }

  fun storeManualCalculation(prisonerId: String, manualEntrySelectedDate: List<ManualEntrySelectedDate>): ManualCalculationResponse {
    val sourceData = prisonService.getPrisonApiSourceData(prisonerId, true)
    val booking = bookingService.getBooking(sourceData, CalculationUserInputs())
    val type = if (hasIndeterminateSentences(booking.bookingId)) CalculationType.MANUAL_INDETERMINATE else CalculationType.MANUAL_DETERMINATE
    val calculationRequest = transform(
      booking,
      serviceUserService.getUsername(),
      CalculationStatus.CONFIRMED,
      sourceData,
      objectMapper,
    ).withType(type)
    return try {
      val savedCalculationRequest = calculationRequestRepository.save(calculationRequest)
      val calculationOutcomes = manualEntrySelectedDate.map { transform(savedCalculationRequest, it) }
      calculationOutcomeRepository.saveAll(calculationOutcomes)
      val enteredDates = writeToNomisAndPublishEvent(prisonerId, booking, savedCalculationRequest.id, calculationOutcomes)
        ?: throw CouldNotSaveManualEntryException("There was a problem saving the dates")
      ManualCalculationResponse(enteredDates, savedCalculationRequest.id)
    } catch (ex: Exception) {
      calculationRequestRepository.save(
        transform(booking, serviceUserService.getUsername(), CalculationStatus.ERROR, sourceData, objectMapper),
      )
      ManualCalculationResponse(emptyMap(), calculationRequest.id)
    }
  }

  @Transactional(readOnly = true)
  fun writeToNomisAndPublishEvent(prisonerId: String, booking: Booking, calculationRequestId: Long, calculationOutcomes: List<CalculationOutcome>): Map<ReleaseDateType, LocalDate?>? {
    val calculationRequest = calculationRequestRepository.findById(calculationRequestId)
      .orElseThrow { EntityNotFoundException("No calculation request exists") }
    val dates = calculationOutcomes.map { ReleaseDateType.valueOf(it.calculationDateType) to it.outcomeDate }.toMap()
    val comment = if (dates.containsKey(ReleaseDateType.None)) INDETERMINATE_COMMENT else DETERMINATE_COMMENT
    val updateOffenderDates = UpdateOffenderDates(
      calculationUuid = calculationRequest.calculationReference,
      submissionUser = serviceUserService.getUsername(),
      keyDates = transform(dates),
      noDates = dates.containsKey(ReleaseDateType.None),
      comment = comment.format(calculationRequest.calculationReference),
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
      return dates
    }.onFailure { error ->
      CalculationTransactionalService.log.error(
        "Failed to send release-dates-changed-event for prisoner ID $prisonerId",
        error,
      )
    }
    return null
  }

  private companion object {
    const val INDETERMINATE_COMMENT = "An Indeterminate (Life) sentence was entered with no dates currently available. This was intentionally recorded as blank. It was entered using the Calculate release dates service. The calculation ID is: %s"
    const val DETERMINATE_COMMENT = "The information shown was manually recorded in the Calculate release dates service. The calculation ID is: %s"
  }
}
