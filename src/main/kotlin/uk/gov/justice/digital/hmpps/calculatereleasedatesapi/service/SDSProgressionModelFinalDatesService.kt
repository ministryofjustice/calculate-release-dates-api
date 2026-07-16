package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.DefaultingOutcome
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.PreLegislationCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.SDSLegislation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.HDCED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Adjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationResult

@Service
class SDSProgressionModelFinalDatesService {

  fun applyFinalDates(
    earlyReleaseCalculation: CalculationResult,
    preLegislationCalculation: PreLegislationCalculation,
    adjustments: Adjustments,
  ): CalculationResult {
    val standardReleaseCalculation = preLegislationCalculation.beforeLegislationAppliedCalculationResult
    val earliestApplicableDate = preLegislationCalculation.legislationApplied.earliestApplicableDate
    val legislation = preLegislationCalculation.legislationApplied.legislation
    require(legislation is SDSLegislation.ProgressionModelLegislation) { "Using progression model defaulting rules for non-progression model legislation" }

    // default to the early release dates for when there is no applicable tranche or further adjustment of dates required
    val mergedDates = earlyReleaseCalculation.dates.toMutableMap()
    val mergedBreakdown = earlyReleaseCalculation.breakdownByReleaseDateType.toMutableMap()

    /*
     * use the standard release date for HDC to support the operational recalculation period where progression model will
     * be enabled but offenders can still be released on their HDCs calculated at 40% or 50% before commencement. Once
     * progression model has commenced, calculation of HDC will be disabled for adult sentences.
     */
    standardReleaseCalculation.dates[HDCED]?.let { mergedDates[HDCED] = it }
    standardReleaseCalculation.breakdownByReleaseDateType[HDCED]?.let { mergedBreakdown[HDCED] = it }

    if (earliestApplicableDate != null) {
      DATE_TYPES_TO_ADJUST_TO_COMMENCEMENT_DATE.forEach { releaseDateType ->
        val early = earlyReleaseCalculation.dates[releaseDateType]
        val standard = standardReleaseCalculation.dates[releaseDateType]

        val awardedDays = getAwardedDays(adjustments)

        // if the standard date is defaulted using PM rules then it was before the tranche date and should be retained here.
        if (standard != null && legislation.applyDefaulting(standard, earliestApplicableDate, awardedDays).outcome == DefaultingOutcome.DEFAULTED) {
          mergedDates[releaseDateType] = standard
          standardReleaseCalculation.breakdownByReleaseDateType[releaseDateType]?.let { standardBreakdown -> mergedBreakdown[releaseDateType] = standardBreakdown }
        } else if (early != null) {
          val defaultingResult = legislation.applyDefaulting(early, earliestApplicableDate, awardedDays)
          mergedDates[releaseDateType] = defaultingResult.date
          earlyReleaseCalculation.breakdownByReleaseDateType[releaseDateType]?.let { earlyBreakdown ->
            mergedBreakdown[releaseDateType] = earlyBreakdown.copy(
              releaseDate = defaultingResult.date,
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
      affectedByProgressionModel = mergedDates != standardReleaseCalculation.dates,
    )
  }

  private fun getAwardedDays(adjustments: Adjustments): Long = (adjustments.getOrEmptyList(AdjustmentType.ADDITIONAL_DAYS_AWARDED).sumOf { it.numberOfDays } - adjustments.getOrEmptyList(AdjustmentType.RESTORATION_OF_ADDITIONAL_DAYS_AWARDED).sumOf { it.numberOfDays }).toLong()

  companion object {
    private val DATE_TYPES_TO_ADJUST_TO_COMMENCEMENT_DATE = listOf(
      ReleaseDateType.CRD,
      ReleaseDateType.PED,
      ReleaseDateType.ERSED,
    )
  }
}
