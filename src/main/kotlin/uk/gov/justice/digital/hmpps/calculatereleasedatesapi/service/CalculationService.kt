package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.FeatureToggles
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationOptions
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationUserInputs

@Service
class CalculationService(
  private val bookingCalculationService: BookingCalculationService,
  private val bookingExtractionService: BookingExtractionService,
  private val bookingTimelineService: BookingTimelineService,
  private val featureToggles: FeatureToggles,
  private val sdsEarlyReleaseDefaultingRulesService: SDSEarlyReleaseDefaultingRulesService,
) {

  fun calculateReleaseDates(booking: Booking, calculationUserInputs: CalculationUserInputs): Pair<Booking, CalculationResult> {
    val options = CalculationOptions(calculationUserInputs.calculateErsed, featureToggles.sdsEarlyRelease)
    val (workingBooking, result) = calcAndExtract(booking.copy(), options)
    return workingBooking to adjustResultsForSDSEarlyReleaseIfRequired(booking, workingBooking, result, options)
  }

  private fun adjustResultsForSDSEarlyReleaseIfRequired(
    originalBooking: Booking,
    workingBookingForPossibleEarlyRelease: Booking,
    resultWithPossibleEarlyRelease: CalculationResult,
    options: CalculationOptions,
  ) = if (sdsEarlyReleaseDefaultingRulesService.requiresRecalculation(workingBookingForPossibleEarlyRelease, resultWithPossibleEarlyRelease)) {
    val (_, resultWithoutEarlyRelease) = calcAndExtract(originalBooking.copy(), options.copy(allowSDSEarlyRelease = false))
    sdsEarlyReleaseDefaultingRulesService.mergeResults(resultWithPossibleEarlyRelease, resultWithoutEarlyRelease)
  } else {
    resultWithPossibleEarlyRelease
  }

  private fun calcAndExtract(
    booking: Booking,
    options: CalculationOptions,
  ): Pair<Booking, CalculationResult> {
    val workingBooking = calculate(booking, options)
    // apply any rules to calculate the dates
    return workingBooking to bookingExtractionService.extract(workingBooking)
  }

  fun calculate(booking: Booking, options: CalculationOptions): Booking {
    var workingBooking: Booking = booking

    // identify the types of the sentences
    workingBooking =
      bookingCalculationService
        .identify(workingBooking, options)

    // calculate the dates within the sentences (Generate initial sentence calculations)
    workingBooking =
      bookingCalculationService
        .calculate(workingBooking, options)

    workingBooking =
      bookingCalculationService
        .createConsecutiveSentences(workingBooking, options)

    workingBooking =
      bookingCalculationService
        .createSingleTermSentences(workingBooking, options)

    workingBooking = bookingTimelineService
      .walkTimelineOfBooking(workingBooking)

    return workingBooking
  }
}
