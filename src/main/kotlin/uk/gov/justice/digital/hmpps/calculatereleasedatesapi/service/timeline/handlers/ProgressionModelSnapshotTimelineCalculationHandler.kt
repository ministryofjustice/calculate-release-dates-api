package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.handlers

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.LegislationName
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.PreLegislationCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.CalculationSnapshot
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.SDS40FinalDatesService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.SnapshotName
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineCalculationEvent.ProgressionModelSnapshotTimelineCalculationEvent
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineCalculator
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineHandleResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineTrackingData

@Service
class ProgressionModelSnapshotTimelineCalculationHandler(
  timelineCalculator: TimelineCalculator,
  private val sds40FinalDatesService: SDS40FinalDatesService,
) : TimelineCalculationHandler<ProgressionModelSnapshotTimelineCalculationEvent>(timelineCalculator) {

  override fun handle(
    event: ProgressionModelSnapshotTimelineCalculationEvent,
    timelineTrackingData: TimelineTrackingData,
  ): TimelineHandleResult {
    with(timelineTrackingData) {
      val applicableLegislation = applicableSdsLegislations.getApplicableLegislation(event.legislation.legislationName)
      val allSentences = releasedSentenceGroups.map { it.sentences }.plus(listOf(currentSentenceGroup))
      val timelineCalculationDate = event.date
      val currentTimelineDateIsTheAllocatedTrancheDate = timelineCalculationDate == applicableLegislation?.earliestApplicableDate
      if (applicableLegislation != null && currentTimelineDateIsTheAllocatedTrancheDate && allSentences.flatten().isNotEmpty()) {
        var latestCalculation = timelineCalculator.getLatestCalculation(allSentences, offender, returnToCustodyDate, snapshots)
        if (applicableSdsLegislations.hasTrancheSet(LegislationName.SDS_40) && SnapshotName.BEFORE_SDS40_TRANCHE in snapshots) {
          // if there was already an SDS40 tranche allocated then apply defaulting and adjustments at this point so that any SDS50 dates that were
          // retained for SDS40 are used in the progression model defaulting and will likely still be retained then as well.
          val preLegislationCalculation = PreLegislationCalculation(snapshots[SnapshotName.BEFORE_SDS40_TRANCHE]!!.result, applicableSdsLegislations.getApplicableLegislation(LegislationName.SDS_40)!!)
          latestCalculation = sds40FinalDatesService.applyFinalDates(latestCalculation, preLegislationCalculation, originalAdjustments, allSentences.flatten())
        }
        snapshots[SnapshotName.BEFORE_PROGRESSION_MODEL_TRANCHE] = CalculationSnapshot(SnapshotName.BEFORE_PROGRESSION_MODEL_TRANCHE, latestCalculation, timelineCalculationDate)
      }
    }
    return TimelineHandleResult()
  }
}
