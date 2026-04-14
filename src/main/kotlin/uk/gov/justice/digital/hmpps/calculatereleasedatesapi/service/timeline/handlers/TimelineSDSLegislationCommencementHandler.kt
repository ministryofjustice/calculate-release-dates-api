package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.handlers

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.ApplicableLegislation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineCalculationEvent.SDSLegislationCommencementTimelineCalculationEvent
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineCalculator
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineHandleResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineTrackingData

@Service
class TimelineSDSLegislationCommencementHandler(timelineCalculator: TimelineCalculator) : TimelineCalculationHandler<SDSLegislationCommencementTimelineCalculationEvent>(timelineCalculator) {

  override fun handle(
    event: SDSLegislationCommencementTimelineCalculationEvent,
    timelineTrackingData: TimelineTrackingData,
  ): TimelineHandleResult {
    with(timelineTrackingData) {
      applicableSdsLegislations.setApplicableLegislation(ApplicableLegislation(event.legislation))
      return TimelineHandleResult(requiresCalculation = false)
    }
  }
}
