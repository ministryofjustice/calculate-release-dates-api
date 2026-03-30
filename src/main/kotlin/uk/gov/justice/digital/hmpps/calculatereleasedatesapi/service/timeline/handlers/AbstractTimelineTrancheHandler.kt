package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.handlers

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ExternalMovementReason
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineCalculator
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineTrackingData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.isAfterOrEqualTo
import java.time.LocalDate

abstract class AbstractTimelineTrancheHandler(timelineCalculator: TimelineCalculator) : TimelineCalculationHandler(timelineCalculator) {
  fun isPersonConsideredOutOfCustodyAtLegislationCommencement(timelineCalculationDate: LocalDate, legislationCommencementDate: LocalDate, timelineTrackingData: TimelineTrackingData): Boolean {
    with(timelineTrackingData) {
      if (isOutOfPrison() && legislationCommencementDate == timelineCalculationDate) {
        // They are out of prison. The following code checking for any exemptions to that.

        // If they were a HDC, ERS or ECSL release then they should not be early released.
        if (listOf(ExternalMovementReason.HDC, ExternalMovementReason.ERS, ExternalMovementReason.ECSL).contains(outOfPrisonStatus!!.release.movementReason)) {
          return true
        }

        // If the person was UAL at tranche commencement then they are subject to early release.
        val ualAtCommencement = previousUalPeriods.any {
          it.first.isBefore(timelineCalculationDate) && it.second.isAfterOrEqualTo(timelineCalculationDate)
        }
        return !ualAtCommencement
      }
      return false
    }
  }
}
