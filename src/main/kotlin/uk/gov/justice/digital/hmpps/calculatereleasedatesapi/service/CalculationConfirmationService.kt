package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import jakarta.persistence.EntityNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.ApprovedDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.ApprovedDatesSubmission
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.CalculationNotFoundException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculatedReleaseDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ManuallyEnteredDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.UpdateOffenderDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.ApprovedDatesSubmissionRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationRequestRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.CalculationTransactionalService.Companion.log

@Service
class CalculationConfirmationService(
  private val calculationRequestRepository: CalculationRequestRepository,
  private val serviceUserService: ServiceUserService,
  private val nomisCommentService: NomisCommentService,
  private val prisonService: PrisonService,
  private val eventService: EventService,
  private val approvedDatesSubmissionRepository: ApprovedDatesSubmissionRepository,
) {
  @Transactional
  fun storeApprovedDates(calculation: CalculatedReleaseDates, approvedDates: List<ManuallyEnteredDate>) {
    val foundCalculation = calculationRequestRepository.findById(calculation.calculationRequestId)
    foundCalculation.map {
      val submittedDatesToSave = approvedDates.map { approvedDate ->
        ApprovedDates(
          calculationDateType = approvedDate.dateType.name,
          outcomeDate = approvedDate.date!!.toLocalDate(),
        )
      }
      val approvedDatesSubmission = ApprovedDatesSubmission(
        calculationRequest = it,
        bookingId = it.bookingId,
        prisonerId = it.prisonerId,
        submittedByUsername = it.calculatedByUsername,
        approvedDates = submittedDatesToSave,
      )
      approvedDatesSubmissionRepository.save(approvedDatesSubmission)
    }
      .orElseThrow { CalculationNotFoundException("Could not find calculation with request id: ${calculation.calculationRequestId}") }
  }

  @Transactional(readOnly = true)
  fun writeToNomisAndPublishEvent(
    prisonerId: String,
    booking: Booking,
    calculation: CalculatedReleaseDates,
    approvedDates: List<ManuallyEnteredDate>?,
    isSpecialistSupport: Boolean? = false,
  ) {
    val calculationRequest = calculationRequestRepository.findById(calculation.calculationRequestId)
      .orElseThrow { EntityNotFoundException("No calculation request exists") }

    val updateOffenderDates = UpdateOffenderDates(
      calculationUuid = calculationRequest.calculationReference,
      submissionUser = serviceUserService.getUsername(),
      keyDates = transform(calculation, approvedDates),
      noDates = false,
      reason = calculationRequest.reasonForCalculation?.nomisReason,
      comment = nomisCommentService.getNomisComment(calculationRequest, isSpecialistSupport!!, approvedDates),
    )
    try {
      prisonService.postReleaseDates(booking.bookingId, updateOffenderDates)
    } catch (ex: Exception) {
      log.error("Nomis write failed: ${ex.message}")
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
  }
}
