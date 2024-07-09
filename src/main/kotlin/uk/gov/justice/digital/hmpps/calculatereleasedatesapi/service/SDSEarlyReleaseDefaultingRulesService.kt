package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationRule
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceIdentificationTrack
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ReleaseDateCalculationBreakdown
import java.time.LocalDate

@Service
class SDSEarlyReleaseDefaultingRulesService(
  @Value("\${sds-early-release-tranches.tranche-one-date}")
  @DateTimeFormat(pattern = "yyyy-MM-dd")
  private val trancheOneCommencementDate: LocalDate,

  @Value("\${sds-early-release-tranches.tranche-two-date}")
  @DateTimeFormat(pattern = "yyyy-MM-dd")
  private val trancheTwoCommencementDate: LocalDate,
) {

  fun requiresRecalculation(booking: Booking, result: CalculationResult): Boolean {
    return hasAnySDSEarlyRelease(booking) && hasAnyReleaseBeforeTrancheCommencement(result)
  }

  fun mergeResults(earlyReleaseResult: CalculationResult, standardReleaseResult: CalculationResult): CalculationResult {
    val dates = earlyReleaseResult.dates.toMutableMap()
    val breakdownByReleaseDateType = earlyReleaseResult.breakdownByReleaseDateType.toMutableMap()

    DATE_TYPES_TO_ADJUST_TO_COMMENCEMENT_DATE.forEach { releaseDateType ->
      mergeDate(releaseDateType, earlyReleaseResult, standardReleaseResult, trancheOneCommencementDate, dates, breakdownByReleaseDateType)
    }

    return CalculationResult(
      dates,
      breakdownByReleaseDateType,
      earlyReleaseResult.otherDates,
      earlyReleaseResult.effectiveSentenceLength,
    )
  }

  private fun hasAnySDSEarlyRelease(anInitialCalc: Booking) =
    anInitialCalc.sentences.any { it.identificationTrack == SentenceIdentificationTrack.SDS_EARLY_RELEASE }

  private fun hasAnyReleaseBeforeTrancheCommencement(result: CalculationResult): Boolean {
    return DATE_TYPES_TO_ADJUST_TO_COMMENCEMENT_DATE
      .mapNotNull { result.dates[it] }
      .any { it < trancheOneCommencementDate }
  }

  private fun mergeDate(
    dateType: ReleaseDateType,
    earlyReleaseResult: CalculationResult,
    standardReleaseResult: CalculationResult,
    commencementDate: LocalDate,
    dates: MutableMap<ReleaseDateType, LocalDate>,
    breakdownByReleaseDateType: MutableMap<ReleaseDateType, ReleaseDateCalculationBreakdown>,
  ) {
    val early = earlyReleaseResult.dates[dateType]
    val standard = standardReleaseResult.dates[dateType]
    if (early != null && early < commencementDate && standard != null && standard >= commencementDate) {
      dates[dateType] = commencementDate
      breakdownByReleaseDateType[dateType] = ReleaseDateCalculationBreakdown(
        setOf(CalculationRule.SDS_EARLY_RELEASE_ADJUSTED_TO_TRANCHE_COMMENCEMENT),
        releaseDate = commencementDate,
        unadjustedDate = early,
      )
    } else if (standard != null && standard < commencementDate) {
      dates[dateType] = standard
      standardReleaseResult.breakdownByReleaseDateType[dateType]?.let {
        breakdownByReleaseDateType[dateType] = it
      }
    }
  }
  companion object {
    private val DATE_TYPES_TO_ADJUST_TO_COMMENCEMENT_DATE = listOf(ReleaseDateType.CRD, ReleaseDateType.ERSED, ReleaseDateType.HDCED, ReleaseDateType.HDCED4PLUS, ReleaseDateType.PED)
  }
}
