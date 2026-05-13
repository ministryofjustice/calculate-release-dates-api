package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.PreLegislationCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Adjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculableSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.SDS40EarlyReleaseDefaultingRulesService

@Service
class SDS40FinalDatesService(
  private val sds40EarlyReleaseDefaultingRulesService: SDS40EarlyReleaseDefaultingRulesService,
  private val timelinePostSDS40TrancheAdjustmentService: TimelinePostSDS40TrancheAdjustmentService,
) {

  fun applyFinalDates(earlyReleaseCalculation: CalculationResult, preLegislationCalculation: PreLegislationCalculation, adjustments: Adjustments, allSentences: List<CalculableSentence>): CalculationResult {
    val calculationPostDefaulting = sds40EarlyReleaseDefaultingRulesService.applySDSEarlyReleaseRulesAndFinalizeDates(
      earlyReleaseCalculation,
      preLegislationCalculation,
      allSentences,
    )
    val earliestApplicableDate = preLegislationCalculation.legislationApplied.earliestApplicableDate
    return if (earliestApplicableDate != null) {
      timelinePostSDS40TrancheAdjustmentService.applyTrancheAdjustmentLogic(
        calculationPostDefaulting,
        adjustments,
        earliestApplicableDate,
      )
    } else {
      calculationPostDefaulting
    }
  }
}
