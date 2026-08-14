package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.handlers

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.ApplicableLegislation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.TrancheAllocationService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineCalculationEvent.SDSTrancheAllocationTimelineCalculationEvent
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineCalculator
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineHandleResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineTrackingData

@Service
class TimelineSDSTrancheCalculationHandler(
  timelineCalculator: TimelineCalculator,
  private val trancheAllocationService: TrancheAllocationService,
) : TimelineCalculationHandler<SDSTrancheAllocationTimelineCalculationEvent>(timelineCalculator) {

  override fun handle(
    event: SDSTrancheAllocationTimelineCalculationEvent,
    timelineTrackingData: TimelineTrackingData,
  ): TimelineHandleResult {
    with(timelineTrackingData) {
      val legislationToApply = event.legislation
      val requiresTrancheAllocation = !applicableSdsLegislations.hasTrancheSet(legislationToApply.legislationName)
      if (requiresTrancheAllocation) {
        trancheAllocationService.allocateTranche(this, legislationToApply)?.let { allocated ->
          applicableSdsLegislations.setApplicableLegislation(ApplicableLegislation(legislationToApply, allocated.date))
          trancheAllocationByLegislationName[legislationToApply.legislationName] = allocated.name
        }
      }
    }
    return TimelineHandleResult()
  }
}
