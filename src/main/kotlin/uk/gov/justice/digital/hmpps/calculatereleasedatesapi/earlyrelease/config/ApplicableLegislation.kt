package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculableSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.StandardDeterminateSentence
import java.time.LocalDate

data class ApplicableLegislation<T : Legislation>(
  val legislation: T,
  val earliestApplicableDate: LocalDate? = null,
) {
  companion object {
    fun ApplicableLegislation<SDSLegislation>.applyToSentence(sentence: CalculableSentence, timelineCalculationDate: LocalDate) {
      sentence.sentenceParts()
        .mapNotNull { sentencePart -> sentencePart as? StandardDeterminateSentence }
        .filter { sdsPart -> legislation.appliesToSentence(sdsPart) }.onEach { sdsPart ->
          // only update the relevant sentence parts to maintain the correct multipliers for consecutive sentences when new legislation only partially applies.
          sdsPart.releaseMultiplier = legislation.releaseMultiplier[sdsPart.identificationTrack]
        }
      sentence.sentenceCalculation.applicableSdsLegislation = this
      sentence.sentenceCalculation.unadjustedReleaseDate.calculationTrigger = sentence.sentenceCalculation.unadjustedReleaseDate.calculationTrigger.copy(
        timelineCalculationDate = timelineCalculationDate,
      )
    }
  }
}
