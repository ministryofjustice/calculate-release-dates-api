package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.handlers

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.FTRLegislations
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.SDSLegislations
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ExternalMovementReason
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.ImportantDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.OutOfPrisonStatus
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineCalculator
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineHandleResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineTrackingData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.isAfterOrEqualTo
import java.time.LocalDate

@Service
class TimelineExternalReleaseMovementCalculationHandler(
  timelineCalculator: TimelineCalculator,
  sdsLegislations: SDSLegislations,
  ftrLegislations: FTRLegislations,
) : TimelineCalculationHandler(timelineCalculator, sdsLegislations, ftrLegislations) {
  override fun handle(timelineCalculationDate: LocalDate, timelineTrackingData: TimelineTrackingData): TimelineHandleResult {
    with(timelineTrackingData) {
      val thisExternalMovement = externalMovements.find { it.movementDate == timelineCalculationDate }!!
      val nextExternalMovement = externalMovements.firstOrNull { it.movementDate > timelineCalculationDate }

      outOfPrisonStatus = OutOfPrisonStatus(
        thisExternalMovement,
        nextExternalMovement,
      )

      val ersRemovalShouldCountAsCustody =
        thisExternalMovement.movementReason == ExternalMovementReason.ERS && (nextExternalMovement?.movementReason == ExternalMovementReason.FAILED_ERS_REMOVAL || timelineCalculationDate.isAfterOrEqualTo(ImportantDates.ERS_STOP_CLOCK_COMMENCEMENT))

      if (!ersRemovalShouldCountAsCustody) {
        if (currentSentenceGroup.isNotEmpty()) {
          latestRelease = timelineCalculationDate to latestRelease.second
        }
      }
    }
    return TimelineHandleResult(requiresCalculation = false, skipCalculationForEntireDate = true)
  }
}
