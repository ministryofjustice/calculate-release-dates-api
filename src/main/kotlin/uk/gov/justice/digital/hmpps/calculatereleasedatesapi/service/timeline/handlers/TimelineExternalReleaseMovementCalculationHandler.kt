package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.handlers

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.FeatureToggles
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.ReleasePointMultipliersConfiguration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.SDS40TrancheConfiguration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ExternalMovementReason
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.ImportantDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineCalculator
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineHandleResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineTrackingData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.isAfterOrEqualTo
import java.time.LocalDate

@Service
class TimelineExternalReleaseMovementCalculationHandler(
  trancheConfiguration: SDS40TrancheConfiguration,
  releasePointConfiguration: ReleasePointMultipliersConfiguration,
  timelineCalculator: TimelineCalculator,
  private val featureToggles: FeatureToggles,
) : TimelineCalculationHandler(trancheConfiguration, releasePointConfiguration, timelineCalculator) {
  override fun handle(timelineCalculationDate: LocalDate, timelineTrackingData: TimelineTrackingData): TimelineHandleResult {
    with(timelineTrackingData) {
      val thisExternalMovement = externalMovements.find { it.movementDate == timelineCalculationDate }!!
      val nextExternalMovement = externalMovements.firstOrNull { it.movementDate > timelineCalculationDate }

      if (thisExternalMovement.movementReason == ExternalMovementReason.ERS) {
        if (nextExternalMovement?.movementReason == ExternalMovementReason.ERS_RETURN ||
          timelineCalculationDate.isAfterOrEqualTo(ImportantDates.ERS_STOP_CLOCK_COMMENCEMENT)
        ) {
          return TimelineHandleResult(requiresCalculation = false)
        }
      }

      if (featureToggles.externalMovementsSds40) {
        inPrison = false
      }
      if (featureToggles.externalMovementsAdjustmentSharing) {
        if (currentSentenceGroup.isNotEmpty()) {
          latestRelease = timelineCalculationDate to latestRelease.second
        }
      }
    }
    return TimelineHandleResult(requiresCalculation = false, skipCalculationForEntireDate = true)
  }
}
