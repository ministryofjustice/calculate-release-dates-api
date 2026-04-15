package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.handlers

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineCalculationEvent
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineCalculator
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineHandleResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineTrackingData

abstract class TimelineCalculationHandler<T : TimelineCalculationEvent>(
  protected val timelineCalculator: TimelineCalculator,
) {
  abstract fun handle(
    event: T,
    timelineTrackingData: TimelineTrackingData,
  ): TimelineHandleResult
}
