package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculableSentence
import java.time.LocalDate

interface SDSLegislationWithTranches :
  SDSLegislation,
  LegislationWithTranches {
  fun isSentenceSubjectToTraches(sentence: CalculableSentence): Boolean
  override val trancheSelectionStrategy: SDSTrancheSelectionStrategy

  fun sentencesToModifyReleaseDates(
    allSentences: List<CalculableSentence>,
    timelineCalculationDate: LocalDate,
  ): List<CalculableSentence> = allSentences.filter {
    it.sentenceCalculation.adjustedDeterminateReleaseDate.isAfter(timelineCalculationDate)
  }
    .filter { sentence -> sentence.sentenceParts().any { this.appliesToSentence(it) } }
}
