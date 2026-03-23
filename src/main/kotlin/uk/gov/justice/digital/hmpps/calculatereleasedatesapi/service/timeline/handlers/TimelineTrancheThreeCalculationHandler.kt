package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.handlers

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.FTRLegislations
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.SDSLegislations
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceIdentificationTrack
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.StandardDeterminateSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineCalculator
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineHandleResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineTrackingData
import java.time.LocalDate

@Service
class TimelineTrancheThreeCalculationHandler(
  timelineCalculator: TimelineCalculator,
  sdsLegislations: SDSLegislations,
  ftrLegislations: FTRLegislations,
) : TimelineCalculationHandler(timelineCalculator, sdsLegislations, ftrLegislations) {

  override fun handle(
    timelineCalculationDate: LocalDate,
    timelineTrackingData: TimelineTrackingData,
  ): TimelineHandleResult {
    with(timelineTrackingData) {
      val sentencesWithT3Exclusion = currentSentenceGroup
        .filter { sentence -> sentence.sentenceParts().any { sentence -> sentence.identificationTrack == SentenceIdentificationTrack.SDS && sentence is StandardDeterminateSentence && sentence.hasAnSDSEarlyReleaseExclusion.trancheThreeExclusion } }
      sentencesWithT3Exclusion.forEach {
        it.sentenceCalculation.unadjustedReleaseDate.calculationTrigger = it.sentenceCalculation.unadjustedReleaseDate.calculationTrigger.copy(timelineCalculationDate = timelineCalculationDate)
      }
      return TimelineHandleResult(requiresCalculation = sentencesWithT3Exclusion.isNotEmpty())
    }
  }
}
