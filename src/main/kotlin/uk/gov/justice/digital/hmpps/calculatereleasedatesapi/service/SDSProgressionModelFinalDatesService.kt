package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.DefaultingOutcome
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.PreLegislationCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.SDSLegislation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.HDCED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationResult

@Service
class SDSProgressionModelFinalDatesService {

  fun applyFinalDates(
    earlyReleaseCalculation: CalculationResult,
    preLegislationCalculation: PreLegislationCalculation,
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

        if (standard != null && early != null && early.isAfter(standard)) {
          // the early date is after the standard likely due to a post-commencement sentence being imposed consecutively. in this case the early date should be retained without defaulting
          mergedDates[releaseDateType] = early
          earlyReleaseCalculation.breakdownByReleaseDateType[releaseDateType]?.let { earlyBreakdown ->
            mergedBreakdown[releaseDateType] = earlyBreakdown
          }
        } else if (standard != null &&
          legislation.applyDefaulting(
            standard,
            earliestApplicableDate,
            getAwardedDaysForReleaseDateType(releaseDateType, standardReleaseCalculation, "pre"),
          ).outcome == DefaultingOutcome.DEFAULTED
        ) {
          // if the standard date is defaulted using PM rules then it was before the tranche date and should be retained here.
          mergedDates[releaseDateType] = standard
          standardReleaseCalculation.breakdownByReleaseDateType[releaseDateType]?.let { standardBreakdown -> mergedBreakdown[releaseDateType] = standardBreakdown }
        } else if (early != null) {
          val defaultingResult = legislation.applyDefaulting(
            early,
            earliestApplicableDate,
            getAwardedDaysForReleaseDateType(releaseDateType, earlyReleaseCalculation, "post"),
          )
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
      affectedByProgressionModel = mergedDates != standardReleaseCalculation.dates || standardReleaseCalculation.affectedByProgressionModel,
    )
  }

  private fun getAwardedDaysForReleaseDateType(releaseDateType: ReleaseDateType, calculation: CalculationResult, preOrPost: String): Long = requireNotNull(calculation.breakdownByReleaseDateType[releaseDateType]?.appliedAdjustments?.awarded) { "Awarded days for the $preOrPost-PM calculation for release date type ${releaseDateType.name} not found" }

  companion object {
    // although a CRD may have previously been an ARD it should not be subject to progression model defaulting so we don't need to worry about comparing the two types.
    private val DATE_TYPES_TO_ADJUST_TO_COMMENCEMENT_DATE = listOf(
      ReleaseDateType.CRD,
      ReleaseDateType.PED,
      ReleaseDateType.ERSED,
    )
  }
}
