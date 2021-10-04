package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.AuthAwareAuthenticationToken
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationStatus
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationStatus.CONFIRMED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.BookingCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationOutcomeRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationRequestRepository
import javax.persistence.EntityNotFoundException

@Service
class CalculationService(
  private val bookingCalculationService: BookingCalculationService,
  private val calculationRequestRepository: CalculationRequestRepository,
  private val calculationOutcomeRepository: CalculationOutcomeRepository,
) {

  fun getCurrentAuthentication(): AuthAwareAuthenticationToken =
    SecurityContextHolder.getContext().authentication as AuthAwareAuthenticationToken?
      ?: throw IllegalStateException("User is not authenticated")

  @Transactional
  fun calculate(booking: Booking, calculationStatus: CalculationStatus): BookingCalculation {
    val calculationRequest =
      calculationRequestRepository.save(transform(booking, getCurrentAuthentication().principal, calculationStatus))
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

    // apply any rules to calculate the dates
    val bookingCalculation = bookingCalculationService.extract(workingBooking)
    bookingCalculation.calculationRequestId = calculationRequest.id
    bookingCalculation.dates.forEach {
      calculationOutcomeRepository.save(transform(calculationRequest, it.key, it.value))
    }

    return bookingCalculation
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
}
