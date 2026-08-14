package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.handlers

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.CalculationSnapshot
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineCalculationEvent.SimpleSnapshotTimelineCalculationEvent
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineCalculator
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineHandleResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineTrackingData

@Service
class SimpleSnapshotTimelineCalculationHandler(timelineCalculator: TimelineCalculator) : TimelineCalculationHandler<SimpleSnapshotTimelineCalculationEvent>(timelineCalculator) {

  override fun handle(
    event: SimpleSnapshotTimelineCalculationEvent,
    timelineTrackingData: TimelineTrackingData,
  ): TimelineHandleResult {
    with(timelineTrackingData) {
      val allSentenceGroups = releasedSentenceGroups.map { it.sentences }.plus(listOf(currentSentenceGroup))
      if (allSentenceGroups.flatten().isNotEmpty()) {
        val latestCalculation = timelineCalculator.getLatestCalculation(allSentenceGroups, offender, returnToCustodyDate, snapshots)
        snapshots[event.snapshotName] = CalculationSnapshot(event.snapshotName, latestCalculation, event.date)
      }
    }
    return TimelineHandleResult()
  }
}
