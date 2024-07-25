package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.FeatureToggles
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SDSEarlyReleaseTranche
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationOptions
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationUserInputs
import java.time.LocalDate

@Service
class CalculationService(
  private val bookingCalculationService: BookingCalculationService,
  private val bookingExtractionService: BookingExtractionService,
  private val bookingTimelineService: BookingTimelineService,
  private val featureToggles: FeatureToggles,
  private val sdsEarlyReleaseDefaultingRulesService: SDSEarlyReleaseDefaultingRulesService,
  private val trancheAllocationService: TrancheAllocationService,
  private val trancheOne: TrancheOne,
  private val trancheTwo: TrancheTwo,
) {

  fun calculateReleaseDates(booking: Booking, calculationUserInputs: CalculationUserInputs): Pair<Booking, CalculationResult> {
    val options = CalculationOptions(calculationUserInputs.calculateErsed, featureToggles.sdsEarlyRelease)
    val (workingBooking, result) = calcAndExtract(booking.copy(), options)
    val tranche = trancheAllocationService.calculateTranche(result, workingBooking)

    val tranchCommencentDate = when (tranche) {
      SDSEarlyReleaseTranche.TRANCHE_0 -> null
      SDSEarlyReleaseTranche.TRANCHE_1 -> trancheOne.trancheCommencementDate
      SDSEarlyReleaseTranche.TRANCHE_2 -> trancheTwo.trancheCommencementDate
    }

    return workingBooking to adjustResultsForSDSEarlyReleaseIfRequired(booking, workingBooking, result, options, tranchCommencentDate, tranche)
  }

  private fun adjustResultsForSDSEarlyReleaseIfRequired(
    originalBooking: Booking,
    workingBookingForPossibleEarlyRelease: Booking,
    resultWithPossibleEarlyRelease: CalculationResult,
    options: CalculationOptions,
    trancheCommencementDate: LocalDate?,
    tranche: SDSEarlyReleaseTranche,
  ) = if (sdsEarlyReleaseDefaultingRulesService.requiresRecalculation(workingBookingForPossibleEarlyRelease, resultWithPossibleEarlyRelease, trancheCommencementDate)) {
    val (_, resultWithoutEarlyRelease) = calcAndExtract(originalBooking.copy(), options.copy(allowSDSEarlyRelease = false))
    sdsEarlyReleaseDefaultingRulesService.mergeResults(resultWithPossibleEarlyRelease, resultWithoutEarlyRelease, trancheCommencementDate, tranche)
  } else {
    resultWithPossibleEarlyRelease.copy(sdsEarlyReleaseTranche = tranche)
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
