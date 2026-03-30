package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.handlers

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.ApplicableLegislation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineCalculator
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineHandleResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineTrackingData
import java.time.LocalDate

@Service
class TimelineSDSLegislationCommencementHandler(timelineCalculator: TimelineCalculator) : AbstractTimelineTrancheHandler(timelineCalculator) {

  override fun handle(
    timelineCalculationDate: LocalDate,
    timelineTrackingData: TimelineTrackingData,
  ): TimelineHandleResult {
    with(timelineTrackingData) {
      val legislationToApply = requireNotNull(currentTimelineCalculationDate.sdsLegislationToApplyOnDate) { "Received an SDS legislation initialisation timeline event without a piece of legislation on $timelineCalculationDate" }
      applicableSdsLegislations.setApplicableLegislation(ApplicableLegislation(legislationToApply))
      return TimelineHandleResult(requiresCalculation = false)
    }
  }
}
