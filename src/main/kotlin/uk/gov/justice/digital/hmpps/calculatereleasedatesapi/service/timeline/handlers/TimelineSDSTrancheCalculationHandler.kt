package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.handlers

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.ApplicableLegislation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.ApplicableLegislation.Companion.applyToSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.LegislationName
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.PreLegislationCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.SDSLegislation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.SDSLegislationWithTranches
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculableSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.TrancheAllocationService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.SDS40FinalDatesService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineCalculationEvent.SDSTrancheTimelineCalculationEvent
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineCalculator
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineHandleResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineTrackingData
import java.time.LocalDate

@Service
class TimelineSDSTrancheCalculationHandler(
  timelineCalculator: TimelineCalculator,
  private val trancheAllocationService: TrancheAllocationService,
  private val sds40FinalDatesService: SDS40FinalDatesService,
) : TimelineCalculationHandler<SDSTrancheTimelineCalculationEvent>(timelineCalculator) {

  override fun handle(
    event: SDSTrancheTimelineCalculationEvent,
    timelineTrackingData: TimelineTrackingData,
  ): TimelineHandleResult {
    with(timelineTrackingData) {
      allocateATrancheIfNoneSetYetForThisLegislation(event.legislation)

      val anySentencesRequiringRecalculation = configureCalculationsForSentencesImpactedByThisTranche(event.legislation, event.date)
      if (!anySentencesRequiringRecalculation) {
        return TimelineHandleResult(requiresCalculation = false)
      }
    }
    return TimelineHandleResult()
  }

  private fun TimelineTrackingData.configureCalculationsForSentencesImpactedByThisTranche(
    legislationToApply: SDSLegislationWithTranches,
    timelineCalculationDate: LocalDate,
  ): Boolean {
    val applicableLegislation = applicableSdsLegislations.getApplicableLegislation(legislationToApply.legislationName)
    val sentencesToModifyReleaseDates = sentencesToModifyReleaseDates(this, timelineCalculationDate, legislationToApply)
    val currentTimelineDateIsTheAllocatedTrancheDate = timelineCalculationDate == applicableLegislation?.earliestApplicableDate
    return if (applicableLegislation != null && currentTimelineDateIsTheAllocatedTrancheDate && sentencesToModifyReleaseDates.isNotEmpty()) {
      val allSentences = releasedSentenceGroups.map { it.sentences }.plus(listOf(currentSentenceGroup))
      var latestCalculation = timelineCalculator.getLatestCalculation(allSentences, offender, returnToCustodyDate)
      if (legislationToApply.legislationName == LegislationName.SDS_PROGRESSION_MODEL && LegislationName.SDS_40 in beforeTrancheCalculations) {
        // if there was already an SDS40 tranche allocated then apply defaulting and adjustments at this point so that any SDS50 dates that were
        // retained for SDS40 are used in the progression model defaulting and will likely still be retained then as well.
        latestCalculation = sds40FinalDatesService.applyFinalDates(latestCalculation, beforeTrancheCalculations[LegislationName.SDS_40]!!, originalAdjustments, allSentences.flatten())
      }
      beforeTrancheCalculations[legislationToApply.legislationName] = PreLegislationCalculation(
        latestCalculation,
        applicableLegislation,
      )
      sentencesToModifyReleaseDates.forEach {
        applicableLegislation.applyToSentence(it, timelineCalculationDate)
        it.sentenceCalculation.adjustments = it.sentenceCalculation.adjustments.copy(
          unusedAdaDays = 0,
          unusedLicenceAdaDays = 0,
        )
      }
      true
    } else {
      // No sentences at tranche date.
      false
    }
  }

  private fun TimelineTrackingData.allocateATrancheIfNoneSetYetForThisLegislation(legislationToApply: SDSLegislation) {
    val requiresTrancheAllocation = !applicableSdsLegislations.hasTrancheSet(legislationToApply.legislationName)
    if (requiresTrancheAllocation && legislationToApply is SDSLegislationWithTranches) {
      trancheAllocationService.allocateTranche(this, legislationToApply)?.let { allocated ->
        applicableSdsLegislations.setApplicableLegislation(ApplicableLegislation(legislationToApply, allocated.date))
        trancheAllocationByLegislationName[legislationToApply.legislationName] = allocated.name
      }
    }
  }

  private fun sentencesToModifyReleaseDates(
    timelineTrackingData: TimelineTrackingData,
    timelineCalculationDate: LocalDate,
    legislation: SDSLegislation,
  ): List<CalculableSentence> = (timelineTrackingData.currentSentenceGroup + timelineTrackingData.licenceSentences).filter {
    it.sentenceCalculation.adjustedDeterminateReleaseDate.isAfter(timelineCalculationDate)
  }
    .filter { sentence -> sentence.sentenceParts().any { legislation.appliesToSentence(it) } }
}
