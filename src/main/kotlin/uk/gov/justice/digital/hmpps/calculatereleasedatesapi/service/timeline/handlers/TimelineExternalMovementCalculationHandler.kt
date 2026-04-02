package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.handlers

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ExternalMovementReason
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.ImportantDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineCalculator
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineHandleResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineTrackingData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.isAfterOrEqualTo
import java.time.LocalDate

@Service
class TimelineExternalMovementCalculationHandler(
  timelineCalculator: TimelineCalculator,
) : TimelineCalculationHandler(timelineCalculator) {
  override fun handle(timelineCalculationDate: LocalDate, timelineTrackingData: TimelineTrackingData): TimelineHandleResult {
    with(timelineTrackingData) {
      val outOfPrisonStatus = externalMovements.stateChangeOnDate(timelineCalculationDate)
        ?: return TimelineHandleResult() // if they were coming into custody then perform a calculation

      val ersRemovalShouldCountAsCustody =
        outOfPrisonStatus.release.movementReason == ExternalMovementReason.ERS && (outOfPrisonStatus.admission?.movementReason == ExternalMovementReason.FAILED_ERS_REMOVAL || timelineCalculationDate.isAfterOrEqualTo(ImportantDates.ERS_STOP_CLOCK_COMMENCEMENT))

      if (!ersRemovalShouldCountAsCustody) {
        if (currentSentenceGroup.isNotEmpty()) {
          latestRelease = timelineCalculationDate to latestRelease.second
        }
      }
    }
    return TimelineHandleResult(requiresCalculation = false, skipCalculationForEntireDate = true)
  }
}
