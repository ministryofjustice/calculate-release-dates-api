package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.handlers

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.FTRLegislations
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.SDSLegislations
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineCalculator
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineHandleResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineTrackingData
import java.time.LocalDate

abstract class TimelineCalculationHandler(
  protected val timelineCalculator: TimelineCalculator,
  protected val sdsLegislations: SDSLegislations,
  protected val ftrLegislations: FTRLegislations,
) {
  abstract fun handle(
    timelineCalculationDate: LocalDate,
    timelineTrackingData: TimelineTrackingData,
  ): TimelineHandleResult
}
