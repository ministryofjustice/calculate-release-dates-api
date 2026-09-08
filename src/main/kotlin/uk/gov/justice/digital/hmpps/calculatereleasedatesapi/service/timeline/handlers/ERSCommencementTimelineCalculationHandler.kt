package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.handlers

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineCalculationEvent.ERSLegislationCommencementTimelineCalculationEvent
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineCalculator
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineHandleResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineTrackingData

@Service
class ERSCommencementTimelineCalculationHandler(timelineCalculator: TimelineCalculator) : TimelineCalculationHandler<ERSLegislationCommencementTimelineCalculationEvent>(timelineCalculator) {

  override fun handle(event: ERSLegislationCommencementTimelineCalculationEvent, timelineTrackingData: TimelineTrackingData): TimelineHandleResult {
    timelineTrackingData.applicableErsLegislation.add(event.legislation)
    return TimelineHandleResult()
  }
}
