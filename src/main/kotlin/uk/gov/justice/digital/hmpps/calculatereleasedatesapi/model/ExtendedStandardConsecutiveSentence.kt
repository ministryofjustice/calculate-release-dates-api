package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import com.fasterxml.jackson.annotation.JsonIgnore
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceIdentificationTrack
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

class ExtendedStandardConsecutiveSentence(orderedStandardSentences: List<ExtendedDeterminateSentence>) : AbstractConsecutiveSentence<ExtendedDeterminateSentence>(
  orderedStandardSentences
) {

  override fun buildString(): String {
    return "ExtendedDeterminateSentence\t:\t\n" +
      "Number of sentences\t:\t${orderedStandardSentences.size}\n" +
      "Sentence Types\t:\t$releaseDateTypes\n" +
      "Number of Days in Sentence\t:\t${getLengthInDays()}\n" +
      sentenceCalculation.buildString(releaseDateTypes)
  }

  override fun getLengthInDays(): Int {
    return 0
  }
}
