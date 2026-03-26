package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.handlers

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.ApplicableLegislation.Companion.applyToSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineCalculator
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineHandleResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineTrackingData
import java.time.LocalDate

@Service
class TimelineSDSLegislationAmendmentHandler(timelineCalculator: TimelineCalculator) : TimelineCalculationHandler(timelineCalculator) {

  override fun handle(
    timelineCalculationDate: LocalDate,
    timelineTrackingData: TimelineTrackingData,
  ): TimelineHandleResult {
    with(timelineTrackingData) {
      val legislationToApply = requireNotNull(currentTimelineCalculationDate.sdsLegislationToApplyOnDate) { "Received an SDS legislation amendment without legislation on $timelineCalculationDate" }
      val sentencesImpactedByAmendment = currentSentenceGroup
        .filter { sentence -> sentence.sentenceParts().any { part -> legislationToApply.appliesToSentence(part) } }
      val applicableLegislation = requireNotNull(applicableSdsLegislations.getApplicableLegislation(legislationToApply.legislationName)) { "Legislation hasn't been initialised for ${legislationToApply.legislationName}" }
      sentencesImpactedByAmendment.forEach {
        applicableLegislation.applyToSentence(it, timelineCalculationDate)
      }
      return TimelineHandleResult(requiresCalculation = sentencesImpactedByAmendment.isNotEmpty())
    }
  }
}
