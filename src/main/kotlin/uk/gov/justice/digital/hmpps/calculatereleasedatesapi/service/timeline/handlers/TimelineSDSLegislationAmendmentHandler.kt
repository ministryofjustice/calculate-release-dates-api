package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.handlers

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.ApplicableLegislation.Companion.applyToSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineCalculationEvent.SDSLegislationAmendmentTimelineCalculationEvent
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineCalculator
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineHandleResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineTrackingData

@Service
class TimelineSDSLegislationAmendmentHandler(timelineCalculator: TimelineCalculator) : TimelineCalculationHandler<SDSLegislationAmendmentTimelineCalculationEvent>(timelineCalculator) {

  override fun handle(
    event: SDSLegislationAmendmentTimelineCalculationEvent,
    timelineTrackingData: TimelineTrackingData,
  ): TimelineHandleResult {
    with(timelineTrackingData) {
      val sentencesImpactedByAmendment = currentSentenceGroup
        .filter { sentence -> sentence.sentenceParts().any { part -> event.legislation.appliesToSentence(part) } }
      val applicableLegislation = requireNotNull(applicableSdsLegislations.getApplicableLegislation(event.legislation.legislationName)) { "Legislation hasn't been initialised for ${event.legislation.legislationName}" }
      sentencesImpactedByAmendment.forEach {
        applicableLegislation.applyToSentence(it, event.date)
      }
      return TimelineHandleResult(requiresCalculation = sentencesImpactedByAmendment.isNotEmpty())
    }
  }
}
