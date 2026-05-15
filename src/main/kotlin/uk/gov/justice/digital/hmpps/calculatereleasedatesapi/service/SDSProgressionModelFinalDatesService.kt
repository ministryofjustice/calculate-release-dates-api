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
    val mergedDates = earlyReleaseCalculation.dates.toMutableMap()
    val mergedBreakdown = earlyReleaseCalculation.breakdownByReleaseDateType.toMutableMap()
    val earliestApplicableDate = preLegislationCalculation.legislationApplied.earliestApplicableDate
    val commencementDate = preLegislationCalculation.legislationApplied.legislation.commencementDate()

    // if there is no tranche then just accept the early release dates as-is
    if (earliestApplicableDate != null) {
      DATE_TYPES_TO_ADJUST_TO_COMMENCEMENT_DATE.forEach { releaseDateType ->
        val early = earlyReleaseCalculation.dates[releaseDateType]
        val standard = standardReleaseCalculation.dates[releaseDateType]
        val awardedDays = getAwardedDays(adjustments)

        if (standard != null && standard.minusDays(awardedDays).isBefore(earliestApplicableDate)) {
          // use the standard date as-is if it's before the tranche date unless they are serving ADAs over the tranche date in which case tranche defaulting applies
          mergedDates[releaseDateType] = standard
          standardReleaseCalculation.breakdownByReleaseDateType[releaseDateType]?.let { standardBreakdown -> mergedBreakdown[releaseDateType] = standardBreakdown }
        } else if (early != null) {
          // only default to tranche date if the release date excluding awarded is earlier. Awarded days are applied to the tranche date
          if (early.minusDays(awardedDays).isBefore(earliestApplicableDate)) {
            val ualPostProgression = getUalPostProgression(adjustments, commencementDate)
            val earlyDefaultedToTracheWithAwarded = earliestApplicableDate.plusDays(awardedDays).plusDays(ualPostProgression)
            mergedDates[releaseDateType] = earlyDefaultedToTracheWithAwarded
            earlyReleaseCalculation.breakdownByReleaseDateType[releaseDateType]?.let { earlyBreakdown ->
              mergedBreakdown[releaseDateType] = earlyBreakdown.copy(
                releaseDate = earlyDefaultedToTracheWithAwarded,
              )
            }
          }
        }
        // the default is just to use the early release date
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
