package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineTrackingData

interface LegislationWithTranches : Legislation {
  val trancheSelectionStrategy: TrancheSelectionStrategy
  val tranches: List<TrancheConfiguration>

  fun anyReasonTheTrancheCannotApply(allocatedTranche: TrancheConfiguration, timelineTrackingData: TimelineTrackingData): Boolean = false
}
