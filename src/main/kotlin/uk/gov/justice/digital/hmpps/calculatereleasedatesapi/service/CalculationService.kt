package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationRule
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SDSEarlyReleaseTranche
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationOptions
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationUserInputs
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceCalculation
import java.time.LocalDate

@Service
class CalculationService(
  private val bookingCalculationService: BookingCalculationService,
  private val bookingExtractionService: BookingExtractionService,
  private val bookingTimelineService: BookingTimelineService,
  private val sdsEarlyReleaseDefaultingRulesService: SDSEarlyReleaseDefaultingRulesService,
  private val trancheAllocationService: TrancheAllocationService,
  private val trancheOne: TrancheOne,
  private val trancheTwo: TrancheTwo,
  private val extractionService: SentencesExtractionService,
  private val objectMapper: ObjectMapper,
) {

  fun deepCopy(sourceBooking: Booking): Booking {
    val json = objectToJson(sourceBooking, objectMapper)
    return objectMapper.readValue(json.toString(), Booking::class.java)
  }
  fun calculateReleaseDates(booking: Booking, calculationUserInputs: CalculationUserInputs, returnLongestPossibleSentences: Boolean = false): Pair<Booking, CalculationResult> {
    val sds40Options = CalculationOptions(calculationUserInputs.calculateErsed, allowSDSEarlyRelease = true)
    val (sds40WorkingBooking, sds40Result) = calcAndExtract(deepCopy(booking), sds40Options)
    val (standardWorkingBooking, standardResult) =
      calcAndExtract(deepCopy(booking), sds40Options.copy(allowSDSEarlyRelease = false))

    val latestReleaseDateFromStandardBooking = extractionService.mostRecentSentence(standardWorkingBooking.getAllExtractableSentences(), SentenceCalculation::adjustedDeterminateReleaseDate).sentenceCalculation.adjustedDeterminateReleaseDate

    val tranche =
      if (returnLongestPossibleSentences || areCalculationsTheSame(sds40Result, standardResult) || latestReleaseDateFromStandardBooking.isBefore(trancheOne.trancheCommencementDate)) {
        SDSEarlyReleaseTranche.TRANCHE_0
        return standardWorkingBooking to bookingExtractionService.extract(standardWorkingBooking)
      } else {
        trancheAllocationService.calculateTranche(sds40Result, sds40WorkingBooking)
      }

    val trancheCommencementDate = when (tranche) {
      SDSEarlyReleaseTranche.TRANCHE_0 -> null
      SDSEarlyReleaseTranche.TRANCHE_1 -> trancheOne.trancheCommencementDate
      SDSEarlyReleaseTranche.TRANCHE_2 -> trancheTwo.trancheCommencementDate
    }

    return sds40WorkingBooking to adjustResultsForSDSEarlyReleaseIfRequired(
      sds40WorkingBooking,
      sds40Result,
      standardWorkingBooking,
      standardResult,
      trancheCommencementDate,
      tranche,
      trancheOne.trancheCommencementDate,
      trancheOne,
    )
  }

  private fun areCalculationsTheSame(sds40Result: CalculationResult, standardResult: CalculationResult): Boolean {
    if (sds40Result.dates != standardResult.dates) {
      return false
    }
    val sds40ImmediateRelease = sds40Result.breakdownByReleaseDateType.any { it.value.rules.contains(CalculationRule.IMMEDIATE_RELEASE) }
    val standardResultImmediateRelease = sds40Result.breakdownByReleaseDateType.any { it.value.rules.contains(CalculationRule.IMMEDIATE_RELEASE) }
    val bothCalculationsImmediateRelease = sds40ImmediateRelease && standardResultImmediateRelease
    return !bothCalculationsImmediateRelease
  }

  private fun adjustResultsForSDSEarlyReleaseIfRequired(
    workingBookingForPossibleEarlyRelease: Booking,
    resultWithPossibleEarlyRelease: CalculationResult,
    standardWorkingBooking: Booking,
    standardCalculationResult: CalculationResult,
    trancheCommencementDate: LocalDate?,
    tranche: SDSEarlyReleaseTranche,
    trancheOneCommencementDate: LocalDate,
    trancheOneIncludingDates: TrancheOne,
  ) = if (sdsEarlyReleaseDefaultingRulesService.requiresRecalculation(workingBookingForPossibleEarlyRelease, resultWithPossibleEarlyRelease, trancheCommencementDate)) {
    sdsEarlyReleaseDefaultingRulesService.mergeResults(
      resultWithPossibleEarlyRelease,
      standardCalculationResult,
      trancheCommencementDate,
      tranche,
      standardWorkingBooking,
      trancheOneCommencementDate,
      trancheOneIncludingDates,
    )
  } else {
    resultWithPossibleEarlyRelease.copy(sdsEarlyReleaseAllocatedTranche = tranche, sdsEarlyReleaseTranche = SDSEarlyReleaseTranche.TRANCHE_0)
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
