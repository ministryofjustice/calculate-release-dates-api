package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationRule
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SDSEarlyReleaseTranche
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceIdentificationTrack
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ReleaseDateCalculationBreakdown
import java.time.LocalDate

@Service
class SDSEarlyReleaseDefaultingRulesService {

  fun requiresRecalculation(booking: Booking, result: CalculationResult, trancheCommencementDate: LocalDate?): Boolean {
    return hasAnySDSEarlyRelease(booking)
  }

  fun mergeResults(
    earlyReleaseResult: CalculationResult,
    standardReleaseResult: CalculationResult,
    trancheCommencementDate: LocalDate?,
    allocatedTranche: SDSEarlyReleaseTranche,
  ): CalculationResult {
    val dates = earlyReleaseResult.dates.toMutableMap()
    val breakdownByReleaseDateType = earlyReleaseResult.breakdownByReleaseDateType.toMutableMap()

    if (hasAnyReleaseBeforeTrancheCommencement(earlyReleaseResult, trancheCommencementDate)) {
      DATE_TYPES_TO_ADJUST_TO_COMMENCEMENT_DATE.forEach { releaseDateType ->
        mergeDate(
          releaseDateType,
          earlyReleaseResult,
          standardReleaseResult,
          trancheCommencementDate,
          dates,
          breakdownByReleaseDateType,
        )
      }
    }

    val calculatedTranche = if (earlyReleaseResult.dates == standardReleaseResult.dates) {
      SDSEarlyReleaseTranche.TRANCHE_0
    } else {
      allocatedTranche
    }

    return CalculationResult(
      dates,
      breakdownByReleaseDateType,
      earlyReleaseResult.otherDates,
      earlyReleaseResult.effectiveSentenceLength,
      sdsEarlyReleaseAllocatedTranche = allocatedTranche,
      sdsEarlyReleaseTranche = calculatedTranche,
    )
  }

  private fun hasAnySDSEarlyRelease(anInitialCalc: Booking) =
    anInitialCalc.sentences.any { it.identificationTrack == SentenceIdentificationTrack.SDS_EARLY_RELEASE }

  private fun hasAnyReleaseBeforeTrancheCommencement(
    result: CalculationResult,
    trancheCommencementDate: LocalDate?,
  ): Boolean {
    return DATE_TYPES_TO_ADJUST_TO_COMMENCEMENT_DATE
      .mapNotNull { result.dates[it] }
      .any { it < trancheCommencementDate }
  }

  private fun mergeDate(
    dateType: ReleaseDateType,
    earlyReleaseResult: CalculationResult,
    standardReleaseResult: CalculationResult,
    commencementDate: LocalDate?,
    dates: MutableMap<ReleaseDateType, LocalDate>,
    breakdownByReleaseDateType: MutableMap<ReleaseDateType, ReleaseDateCalculationBreakdown>,
  ) {
    val early = earlyReleaseResult.dates[dateType]
    val standard = standardReleaseResult.dates[dateType]
    if (commencementDate != null && early != null && early < commencementDate && standard != null && standard >= commencementDate) {
      dates[dateType] = commencementDate
      breakdownByReleaseDateType[dateType] = ReleaseDateCalculationBreakdown(
        setOf(CalculationRule.SDS_EARLY_RELEASE_ADJUSTED_TO_TRANCHE_COMMENCEMENT),
        releaseDate = commencementDate,
        unadjustedDate = early,
      )
    } else if (standard != null && ((commencementDate != null && standard.isBefore(commencementDate)) || commencementDate == null)) {
      dates[dateType] = standard
      standardReleaseResult.breakdownByReleaseDateType[dateType]?.let {
        breakdownByReleaseDateType[dateType] = it
      }
      // Handle TUSED adjustment to standard when normal CRD is being used.
      if (dateType == ReleaseDateType.CRD && standardReleaseResult.dates.containsKey(ReleaseDateType.TUSED)) {
        standardReleaseResult.dates[ReleaseDateType.TUSED]?.let {
          dates[ReleaseDateType.TUSED] = it
        }
        standardReleaseResult.breakdownByReleaseDateType[ReleaseDateType.TUSED]?.let {
          breakdownByReleaseDateType[ReleaseDateType.TUSED] = it
        }
      }
    }
  }

  companion object {
    private val DATE_TYPES_TO_ADJUST_TO_COMMENCEMENT_DATE = listOf(
      ReleaseDateType.CRD,
      ReleaseDateType.ERSED,
      ReleaseDateType.HDCED,
      ReleaseDateType.HDCED4PLUS,
      ReleaseDateType.PED,
    )
  }
}
