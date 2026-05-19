package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.PreLegislationCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Adjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationResult
import java.time.LocalDate

@Service
class SDSProgressionModelFinalDatesService {

  fun applyFinalDates(
    earlyReleaseCalculation: CalculationResult,
    preLegislationCalculation: PreLegislationCalculation,
    adjustments: Adjustments,
  ): CalculationResult {
    val standardReleaseCalculation = preLegislationCalculation.beforeLegislationAppliedCalculationResult
    val earliestApplicableDate = preLegislationCalculation.legislationApplied.earliestApplicableDate
    val commencementDate = preLegislationCalculation.legislationApplied.legislation.commencementDate()

    // default to the early release dates in the scenario no defaulting is required or there is no applicable tranche
    val mergedDates = earlyReleaseCalculation.dates.toMutableMap()
    val mergedBreakdown = earlyReleaseCalculation.breakdownByReleaseDateType.toMutableMap()

    if (earliestApplicableDate != null) {
      DATE_TYPES_TO_ADJUST_TO_COMMENCEMENT_DATE.forEach { releaseDateType ->
        val early = earlyReleaseCalculation.dates[releaseDateType]
        val standard = standardReleaseCalculation.dates[releaseDateType]

        /*
         * ADAs and UAL occurring post progression should always be included in the final release dates. We remove them when comparing
         * to the standard release date as if they would be due for release without them then they are not eligible for early release.
         * We remove them when deciding whether to default to the tranche commencement date as they will be added to the tranche commencement
         * date if they are defaulted and could be served twice in this scenario. If we did not add them to the tranche date then they
         * may not be fully served.
         */
        val additionalDaysToBeAppliedPostCommencement = getAwardedDays(adjustments) + getUalPostProgression(adjustments, commencementDate)

        if (standard != null && standard.minusDays(additionalDaysToBeAppliedPostCommencement).isBefore(earliestApplicableDate)) {
          mergedDates[releaseDateType] = standard
          standardReleaseCalculation.breakdownByReleaseDateType[releaseDateType]?.let { standardBreakdown -> mergedBreakdown[releaseDateType] = standardBreakdown }
        } else if (early != null && early.minusDays(additionalDaysToBeAppliedPostCommencement).isBefore(earliestApplicableDate)) {
          val earlyDefaultedToTracheWithAwarded = earliestApplicableDate.plusDays(additionalDaysToBeAppliedPostCommencement)
          mergedDates[releaseDateType] = earlyDefaultedToTracheWithAwarded
          earlyReleaseCalculation.breakdownByReleaseDateType[releaseDateType]?.let { earlyBreakdown ->
            mergedBreakdown[releaseDateType] = earlyBreakdown.copy(
              releaseDate = earlyDefaultedToTracheWithAwarded,
            )
          }
        }
      }
    }

    return CalculationResult(
      dates = mergedDates,
      breakdownByReleaseDateType = mergedBreakdown,
      otherDates = earlyReleaseCalculation.otherDates,
      effectiveSentenceLength = earlyReleaseCalculation.effectiveSentenceLength,
      sentencesImpactingFinalReleaseDate = earlyReleaseCalculation.sentencesImpactingFinalReleaseDate,
    )
  }

  private fun getAwardedDays(adjustments: Adjustments): Long = (adjustments.getOrEmptyList(AdjustmentType.ADDITIONAL_DAYS_AWARDED).sumOf { it.numberOfDays } - adjustments.getOrEmptyList(AdjustmentType.RESTORATION_OF_ADDITIONAL_DAYS_AWARDED).sumOf { it.numberOfDays }).toLong()

  fun getUalPostProgression(adjustments: Adjustments, commencementDate: LocalDate) = adjustments.getOrEmptyList(AdjustmentType.UNLAWFULLY_AT_LARGE)
    .filter { it.appliesToSentencesFrom >= commencementDate }
    .sumOf { it.numberOfDays }
    .toLong()

  companion object {
    private val DATE_TYPES_TO_ADJUST_TO_COMMENCEMENT_DATE = listOf(
      ReleaseDateType.CRD,
      ReleaseDateType.PED,
    )
  }
}
