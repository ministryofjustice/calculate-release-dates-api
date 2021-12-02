package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import com.fasterxml.jackson.annotation.JsonIgnore
import org.threeten.extra.LocalDateRange
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceIdentificationTrack
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit


class SingleTermSentence(
  override val sentencedAt: LocalDate,
  override val offence: Offence,
  val sentences: List<Sentence>
) : IdentifiableSentence, CalculableSentence, ExtractableSentence {
  constructor(sentences: List<Sentence>) :
    this(sentences.minOf(Sentence::sentencedAt),
      sentences.map(Sentence::offence).minByOrNull(Offence::committedAt)!!,
      sentences)
  @JsonIgnore
  override lateinit var sentenceCalculation: SentenceCalculation
  @JsonIgnore
  override lateinit var identificationTrack: SentenceIdentificationTrack

  @JsonIgnore
  override lateinit var releaseDateTypes: List<ReleaseDateType>

  fun buildString(): String {
    return "SingleTermSentence\t:\t\n" +
      "Number of sentences\t:\t${sentences.size}\n" +
      "Sentence Types\t:\t$releaseDateTypes\n" +
      "Number of Days in Sentence\t:\t${getLengthInDays()}\n" +
      sentenceCalculation.buildString(releaseDateTypes)
  }

  override fun getLengthInDays(): Int {
    val firstSentence = sentences.get(0)
    val secondSentence = sentences.get(1)
    val durationElements: MutableMap<ChronoUnit, Long> = mutableMapOf()
    durationElements[ChronoUnit.DAYS] = ChronoUnit.DAYS.between(
      earliestSentencedAt(firstSentence, secondSentence),
      latestExpiryDate(firstSentence, secondSentence)?.plusDays(1L)
    )
    return Duration(durationElements).getLengthInDays(sentencedAt)
  }

  private fun earliestSentencedAt(firstSentence: Sentence, secondSentence: Sentence): LocalDate {
    return if (firstSentence.sentencedAt.isBefore(secondSentence.sentencedAt)) {
      firstSentence.sentencedAt
    } else {
      secondSentence.sentencedAt
    }
  }

  private fun latestExpiryDate(firstSentence: Sentence, secondSentence: Sentence): LocalDate? {
    return if (
      firstSentence.sentenceCalculation.expiryDate?.isAfter(secondSentence.sentenceCalculation.expiryDate) == true
    ) {
      firstSentence.sentenceCalculation.expiryDate
    } else {
      secondSentence.sentenceCalculation.expiryDate
    }
  }
}
