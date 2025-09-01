package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import jakarta.persistence.EntityNotFoundException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.ApprovedDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.ApprovedDatesSubmission
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.CalculationNotFoundException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.CrdWebException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.NomisResourceLockedException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculatedReleaseDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ManualEntrySelectedDate
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
  fun storeApprovedDates(calculation: CalculatedReleaseDates, approvedDates: List<ManualEntrySelectedDate>) {
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

  @Transactional(readOnly = true, propagation = Propagation.SUPPORTS)
  fun writeToNomisAndPublishEvent(
    prisonerId: String,
    booking: Booking,
    calculation: CalculatedReleaseDates,
    approvedDates: List<ManualEntrySelectedDate>?,
    isSpecialistSupport: Boolean? = false,
  ) {
    val request = calculationRequestRepository.findById(calculation.calculationRequestId)
      .orElseThrow { EntityNotFoundException("No calculation request exists") }

    val payload = buildUpdateOffenderDates(request, approvedDates, isSpecialistSupport == true, calculation)

    try {
      prisonService.postReleaseDates(booking.bookingId, payload)
    } catch (ex: RestClientResponseException) {
      throw mapNomisHttpException(ex, prisonerId, booking.bookingId)
    } catch (_: RestClientException) {
      throw nomisBadGateway(prisonerId, booking.bookingId)
    } catch (_: Exception) {
      throw nomisUnexpected(prisonerId, booking.bookingId)
    }
    publishReleaseDatesChangedOrLog(prisonerId, booking.bookingId)
  }

  private fun buildUpdateOffenderDates(
    request: CalculationRequest,
    approved: List<ManualEntrySelectedDate>?,
    specialistSupport: Boolean,
    calculation: CalculatedReleaseDates,
  ): UpdateOffenderDates = UpdateOffenderDates(
    calculationUuid = request.calculationReference,
    submissionUser = serviceUserService.getUsername(),
    keyDates = transform(calculation, approved),
    noDates = false,
    reason = request.reasonForCalculation?.nomisReason,
    comment = nomisCommentService.getNomisComment(request, specialistSupport, approved),
  )

  private fun mapNomisHttpException(
    ex: RestClientResponseException,
    prisonerId: String,
    bookingId: Long,
  ): CrdWebException = if (ex.statusCode.value() == 423) {
    NomisResourceLockedException(
      "NOMIS is locked for prisonerId=$prisonerId, bookingId=$bookingId",
    )
  } else {
    nomisBadGateway(prisonerId, bookingId)
  }

  private fun nomisBadGateway(
    prisonerId: String,
    bookingId: Long,
  ) = CrdWebException(
    message = "Failed to write release dates to NOMIS for prisonerId=$prisonerId, bookingId=$bookingId",
    status = HttpStatus.BAD_GATEWAY,
    code = "NOMIS_WRITE_FAILED",
  )

  private fun nomisUnexpected(
    prisonerId: String,
    bookingId: Long,
  ) = CrdWebException(
    message = "Unexpected error writing release dates for prisonerId=$prisonerId, bookingId=$bookingId",
    status = HttpStatus.INTERNAL_SERVER_ERROR,
    code = "NOMIS_WRITE_FAILED",
  )
  private fun publishReleaseDatesChangedOrLog(prisonerId: String, bookingId: Long) {
    runCatching { eventService.publishReleaseDatesChangedEvent(prisonerId, bookingId) }
      .onFailure { log.error("Failed to publish release-dates-changed-event for prisonerId=$prisonerId", it) }
  }
}
