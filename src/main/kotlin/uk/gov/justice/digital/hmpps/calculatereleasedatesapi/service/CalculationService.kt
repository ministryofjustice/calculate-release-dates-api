package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.BookingCalculation

@Service
class CalculationService(
  private val bookingCalculationService: BookingCalculationService
) {

  fun calculate(booking: Booking): BookingCalculation {
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
    return bookingCalculationService
      .extract(workingBooking)
  }
}
