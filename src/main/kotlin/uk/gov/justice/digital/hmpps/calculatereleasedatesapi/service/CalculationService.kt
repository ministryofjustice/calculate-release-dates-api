package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.AuthAwareAuthenticationToken
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationStatus
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationStatus.CONFIRMED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationStatus.PRELIMINARY
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceType.CRD
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceType.LED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceType.SED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceType.SLED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.PreconditionFailedException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.BookingCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationBreakdown
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.OffenderKeyDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.UpdateOffenderDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationOutcomeRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationRequestRepository
import javax.persistence.EntityNotFoundException

@Service
@Suppress("LongParameterList")
class CalculationService(
  private val bookingCalculationService: BookingCalculationService,
  private val bookingExtractionService: BookingExtractionService,
  private val calculationRequestRepository: CalculationRequestRepository,
  private val calculationOutcomeRepository: CalculationOutcomeRepository,
  private val objectMapper: ObjectMapper,
  private val prisonApiClient: PrisonApiClient,
  private val domainEventPublisher: DomainEventPublisher,
) {

  fun getCurrentAuthentication(): AuthAwareAuthenticationToken =
    SecurityContextHolder.getContext().authentication as AuthAwareAuthenticationToken?
      ?: throw IllegalStateException("User is not authenticated")

  @Transactional
  fun calculate(booking: Booking, calculationStatus: CalculationStatus): BookingCalculation {
    val calculationRequest =
      calculationRequestRepository.save(
        transform(booking, getCurrentAuthentication().principal, calculationStatus, objectMapper)
      )

    val workingBooking = calculate(booking)

    // apply any rules to calculate the dates
    val bookingCalculation = bookingExtractionService.extract(workingBooking)
    bookingCalculation.calculationRequestId = calculationRequest.id
    bookingCalculation.dates.forEach {
      calculationOutcomeRepository.save(transform(calculationRequest, it.key, it.value))
    }

    return bookingCalculation
  }

  private fun calculate(booking: Booking): Booking {
    var workingBooking: Booking = booking.copy()

    // identify the types of the sentences
    workingBooking =
      bookingCalculationService
        .identify(workingBooking)

    // associateConsecutive the types of the sentences
    workingBooking =
      bookingCalculationService
        .associateConsecutive(workingBooking)

    // calculate the dates within the sentences (Generate initial sentence calculations)
    workingBooking =
      bookingCalculationService
        .calculate(workingBooking)

    // aggregate appropriate concurrent sentences
    workingBooking =
      bookingCalculationService
        .combineConcurrent(workingBooking)

    // aggregation the consecutive sentences
    workingBooking =
      bookingCalculationService
        .combineConsecutive(workingBooking)

    return workingBooking
  }

  @Transactional(readOnly = true)
  fun calculateWithBreakdown(booking: Booking): CalculationBreakdown {
    val originalBooking = booking.deepCopy()
    val workingBooking = calculate(booking)
    return transform(workingBooking, originalBooking)
  }

  @Transactional(readOnly = true)
  fun findConfirmedCalculationResults(prisonerId: String, bookingId: Long): BookingCalculation {
    val calculationRequest =
      calculationRequestRepository.findFirstByPrisonerIdAndBookingIdAndCalculationStatusOrderByCalculatedAtAsc(
        prisonerId,
        bookingId,
        CONFIRMED.name
      ).orElseThrow {
        EntityNotFoundException("No confirmed calculation exists for prisoner $prisonerId and bookingId $bookingId")
      }

    return transform(calculationRequest)
  }

  @Transactional(readOnly = true)
  fun findCalculationResults(calculationRequestId: Long): BookingCalculation {
    val calculationRequest =
      calculationRequestRepository.findById(calculationRequestId).orElseThrow {
        EntityNotFoundException("No calculation results exist for calculationRequestId $calculationRequestId ")
      }

    return transform(calculationRequest)
  }

  @Transactional(readOnly = true)
  fun getBooking(calculationRequestId: Long): Booking {
    val calculationRequest =
      calculationRequestRepository.findById(calculationRequestId).orElseThrow {
        EntityNotFoundException("No calculation results exist for calculationRequestId $calculationRequestId ")
      }

    return objectMapper.treeToValue(calculationRequest.inputData, Booking::class.java)
  }

  @Transactional(readOnly = true)
  fun validateConfirmationRequest(calculationRequestId: Long, booking: Booking) {
    val calculationRequest =
      calculationRequestRepository.findByIdAndCalculationStatus(
        calculationRequestId,
        PRELIMINARY.name
      ).orElseThrow {
        EntityNotFoundException("No preliminary calculation exists for calculationRequestId $calculationRequestId")
      }

    if (calculationRequest.inputData.hashCode() != bookingToJson(booking, objectMapper).hashCode()) {
      throw PreconditionFailedException("The booking data used for the preliminary calculation has changed")
    }
  }

  @Transactional(readOnly = true)
  @Suppress("TooGenericExceptionCaught")
  fun writeToNomisAndPublishEvent(prisonerId: String, bookingId: Long, calculation: BookingCalculation) {
    val calculationRequest = calculationRequestRepository.findById(calculation.calculationRequestId)
      .orElseThrow { EntityNotFoundException("No calculation request exists") }
    val updateOffenderDates = UpdateOffenderDates(
      calculationUuid = calculationRequest.calculationReference,
      submissionUser = getCurrentAuthentication().principal,
      keyDates =  OffenderKeyDates(
        calculation.dates[CRD],
        calculation.dates[SLED] ?: calculation.dates[LED],
        calculation.dates[SLED] ?: calculation.dates[SED]
      )
    )
    try {
      prisonApiClient.postReleaseDates(bookingId, updateOffenderDates)
    } catch (ex: Exception) {
      log.error("Nomis write failed: ${ex.message}")
      throw EntityNotFoundException(
        "Writing release dates to NOMIS failed for prisonerId $prisonerId " +
          "and bookingId $bookingId"
      )
    }
    domainEventPublisher.publishReleaseDateChange(prisonerId, bookingId)
  }

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}
