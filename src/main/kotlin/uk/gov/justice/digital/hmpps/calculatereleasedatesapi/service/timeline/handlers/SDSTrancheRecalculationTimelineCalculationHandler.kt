package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.handlers

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.ApplicableLegislation.Companion.applyToSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineCalculationEvent.SDSTrancheRecalculationTimelineCalculationEvent
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineCalculator
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineHandleResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineTrackingData

@Service
class SDSTrancheRecalculationTimelineCalculationHandler(
  timelineCalculator: TimelineCalculator,
) : TimelineCalculationHandler<SDSTrancheRecalculationTimelineCalculationEvent>(timelineCalculator) {

  override fun handle(
    event: SDSTrancheRecalculationTimelineCalculationEvent,
    timelineTrackingData: TimelineTrackingData,
  ): TimelineHandleResult {
    with(timelineTrackingData) {
      val legislationToApply = event.legislation
      val timelineCalculationDate = event.date
      val applicableLegislation = applicableSdsLegislations.getApplicableLegislation(legislationToApply.legislationName)
      val sentencesToModifyReleaseDates = legislationToApply.sentencesToModifyReleaseDates(timelineTrackingData.currentSentenceGroup + timelineTrackingData.licenceSentences, timelineCalculationDate)
      val currentTimelineDateIsTheAllocatedTrancheDate = timelineCalculationDate == applicableLegislation?.earliestApplicableDate
      val anySentencesRequiringRecalculation = if (applicableLegislation != null && currentTimelineDateIsTheAllocatedTrancheDate && sentencesToModifyReleaseDates.isNotEmpty()) {
        sentencesToModifyReleaseDates.forEach {
          applicableLegislation.applyToSentence(it, timelineCalculationDate)
          it.sentenceCalculation.adjustments = it.sentenceCalculation.adjustments.copy(
            unusedAdaDays = 0,
            unusedLicenceAdaDays = 0,
          )
        }
        true
      } else {
        // No sentences at tranche date.
        false
      }
      if (!anySentencesRequiringRecalculation) {
        return TimelineHandleResult(requiresCalculation = false)
      }
    }
    return TimelineHandleResult()
  }
}
