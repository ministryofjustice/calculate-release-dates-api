package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationOptions
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationOutput
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationUserInputs
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.BookingTimelineService

@Service
class CalculationService(
  private val bookingCalculationService: BookingCalculationService,
  private val bookingTimelineService: BookingTimelineService,
) {

  fun calculateReleaseDates(
    booking: Booking,
    calculationUserInputs: CalculationUserInputs,
  ): CalculationOutput {
    val options = CalculationOptions(calculationUserInputs.calculateErsed, allowSDSEarlyRelease = true)
    // identify the types of the sentences
    bookingCalculationService
      .identify(booking, options)

    val sentencesToCalculate = bookingCalculationService.getSentencesToCalculate(booking, options)

    return bookingTimelineService.calculate(sentencesToCalculate, booking.adjustments, booking.offender, booking.returnToCustodyDate, options)
  }
}
