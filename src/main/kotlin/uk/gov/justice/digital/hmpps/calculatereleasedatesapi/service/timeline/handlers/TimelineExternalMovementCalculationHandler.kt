package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.handlers

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ExternalMovementReason
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.ImportantDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineCalculationEvent.ExternalMovementTimelineCalculationEvent
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineCalculator
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineHandleResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineTrackingData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.isAfterOrEqualTo

@Service
class TimelineExternalMovementCalculationHandler(
  timelineCalculator: TimelineCalculator,
) : TimelineCalculationHandler<ExternalMovementTimelineCalculationEvent>(timelineCalculator) {
  override fun handle(event: ExternalMovementTimelineCalculationEvent, timelineTrackingData: TimelineTrackingData): TimelineHandleResult {
    with(timelineTrackingData) {
      val outOfPrisonStatus = externalMovements.stateChangeOnDate(event.date)
        ?: return TimelineHandleResult() // if they were coming into custody then perform a calculation

      val ersRemovalShouldCountAsCustody =
        outOfPrisonStatus.release.movementReason == ExternalMovementReason.ERS && (outOfPrisonStatus.admission?.movementReason == ExternalMovementReason.FAILED_ERS_REMOVAL || event.date.isAfterOrEqualTo(ImportantDates.ERS_STOP_CLOCK_COMMENCEMENT))

      if (!ersRemovalShouldCountAsCustody) {
        if (currentSentenceGroup.isNotEmpty()) {
          latestRelease = event.date to latestRelease.second
        }
      }
    }
    return TimelineHandleResult(requiresCalculation = false, skipCalculationForEntireDate = true)
  }
}
