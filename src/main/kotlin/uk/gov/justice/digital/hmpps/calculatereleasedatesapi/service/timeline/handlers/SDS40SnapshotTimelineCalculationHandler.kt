package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.handlers

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.CalculationSnapshot
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.SnapshotName
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineCalculationEvent.SDS40SnapshotTimelineCalculationEvent
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineCalculator
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineHandleResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineTrackingData

@Service
class SDS40SnapshotTimelineCalculationHandler(
  timelineCalculator: TimelineCalculator,
) : TimelineCalculationHandler<SDS40SnapshotTimelineCalculationEvent>(timelineCalculator) {

  override fun handle(
    event: SDS40SnapshotTimelineCalculationEvent,
    timelineTrackingData: TimelineTrackingData,
  ): TimelineHandleResult {
    with(timelineTrackingData) {
      val legislationToApply = event.legislation
      val timelineCalculationDate = event.date
      val applicableLegislation = applicableSdsLegislations.getApplicableLegislation(legislationToApply.legislationName)
      val sentencesToModifyReleaseDates = legislationToApply.sentencesToModifyReleaseDates(timelineTrackingData.currentSentenceGroup + timelineTrackingData.licenceSentences, timelineCalculationDate)
      val currentTimelineDateIsTheAllocatedTrancheDate = timelineCalculationDate == applicableLegislation?.earliestApplicableDate
      if (applicableLegislation != null && currentTimelineDateIsTheAllocatedTrancheDate && sentencesToModifyReleaseDates.isNotEmpty()) {
        val allSentences = releasedSentenceGroups.map { it.sentences }.plus(listOf(currentSentenceGroup))
        val latestCalculation = timelineCalculator.getLatestCalculation(allSentences, offender, returnToCustodyDate, snapshots)
        snapshots[SnapshotName.BEFORE_SDS40_TRANCHE] = CalculationSnapshot(SnapshotName.BEFORE_SDS40_TRANCHE, latestCalculation, timelineCalculationDate)
      }
    }
    return TimelineHandleResult()
  }
}
