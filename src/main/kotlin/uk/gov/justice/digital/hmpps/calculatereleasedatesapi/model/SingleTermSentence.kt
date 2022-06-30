package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import com.fasterxml.jackson.annotation.JsonIgnore
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceIdentificationTrack
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class SingleTermSentence(
  override val sentencedAt: LocalDate,
  override val offence: Offence,
  val standardSentences: List<StandardDeterminateSentence>
) : CalculableSentence {
  constructor(standardSentences: List<StandardDeterminateSentence>) :
    this(
      standardSentences.minOf(StandardDeterminateSentence::sentencedAt),
      standardSentences.map(StandardDeterminateSentence::offence).minByOrNull(Offence::committedAt)!!,
      standardSentences
    )

  override val recallType: RecallType?
    get() {
      return standardSentences[0].recallType
    }

  @JsonIgnore
  override lateinit var sentenceCalculation: SentenceCalculation
  @JsonIgnore
  override lateinit var identificationTrack: SentenceIdentificationTrack

  @JsonIgnore
  override lateinit var releaseDateTypes: List<ReleaseDateType>

  override fun buildString(): String {
    return "SingleTermSentence\t:\t\n" +
      "Number of sentences\t:\t${standardSentences.size}\n" +
      "Sentence Types\t:\t$releaseDateTypes\n" +
      "Number of Days in Sentence\t:\t${getLengthInDays()}\n" +
      sentenceCalculation.buildString(releaseDateTypes)
  }

  fun combinedDuration(): Duration {
    val firstSentence = standardSentences.get(0)
    val secondSentence = standardSentences.get(1)
    val durationElements: MutableMap<ChronoUnit, Long> = mutableMapOf()
    durationElements[ChronoUnit.DAYS] = ChronoUnit.DAYS.between(
      earliestSentencedAt(firstSentence, secondSentence),
      latestExpiryDate(firstSentence, secondSentence)?.plusDays(1L)
    )
    return Duration(durationElements)
  }

  override fun getLengthInDays(): Int {
    return combinedDuration().getLengthInDays(sentencedAt)
  }

  private fun earliestSentencedAt(firstStandardSentence: StandardDeterminateSentence, secondStandardSentence: StandardDeterminateSentence): LocalDate {
    return if (firstStandardSentence.sentencedAt.isBefore(secondStandardSentence.sentencedAt)) {
      firstStandardSentence.sentencedAt
    } else {
      secondStandardSentence.sentencedAt
    }
  }

  private fun latestExpiryDate(firstStandardSentence: StandardDeterminateSentence, secondStandardSentence: StandardDeterminateSentence): LocalDate? {
    return if (
      firstStandardSentence.sentenceCalculation.expiryDate?.isAfter(secondStandardSentence.sentenceCalculation.expiryDate) == true
    ) {
      firstStandardSentence.sentenceCalculation.expiryDate
    } else {
      secondStandardSentence.sentenceCalculation.expiryDate
    }
  }
}
