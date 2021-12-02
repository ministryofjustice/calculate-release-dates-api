package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import com.fasterxml.jackson.annotation.JsonIgnore
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceIdentificationTrack
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class ConsecutiveSentence(
  override val sentencedAt: LocalDate,
  override val offence: Offence,
  val orderedSentences: List<Sentence>
) : IdentifiableSentence, CalculableSentence, ExtractableSentence {
  constructor(orderedSentences: List<Sentence>) :
    this(
      orderedSentences.minOf(Sentence::sentencedAt),
      orderedSentences.map(Sentence::offence).minByOrNull(Offence::committedAt)!!,
      orderedSentences
    )
  @JsonIgnore
  override lateinit var sentenceCalculation: SentenceCalculation
  @JsonIgnore
  override lateinit var identificationTrack: SentenceIdentificationTrack

  @JsonIgnore
  override lateinit var releaseDateTypes: List<ReleaseDateType>

  fun buildString(): String {
    return "ConsecutiveSentence\t:\t\n" +
      "Number of sentences\t:\t${orderedSentences.size}\n" +
      "Sentence Types\t:\t$releaseDateTypes\n" +
      "Number of Days in Sentence\t:\t${getLengthInDays()}\n" +
      sentenceCalculation.buildString(releaseDateTypes)
  }

  override fun getLengthInDays(): Int {
    var date = sentencedAt
    orderedSentences.forEach {
      date = date.plusDays(it.duration.getLengthInDays(date).toLong())
    }
    return (ChronoUnit.DAYS.between(sentencedAt, date)).toInt()
  }
}
