package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import com.fasterxml.jackson.annotation.JsonIgnore
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceIdentificationTrack
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class DtoSingleTermSentence(
  override val sentencedAt: LocalDate,
  override val offence: Offence,
  override val standardSentences: List<AbstractSentence>
) : SingleTermed {
  constructor(standardSentences: List<AbstractSentence>) :
    this(
      standardSentences.minOf(AbstractSentence::sentencedAt),
      standardSentences.map(AbstractSentence::offence).minByOrNull(Offence::committedAt)!!,
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
  override lateinit var releaseDateTypes: ReleaseDateTypes

  override fun buildString(): String {
    return "DtoSingleTermSentence\t:\t\n" +
      "Number of sentences\t:\t${standardSentences.size}\n" +
      "Sentence Types\t:\t$releaseDateTypes\n" +
      "Number of Days in Sentence\t:\t${getLengthInDays()}\n" +
      sentenceCalculation.buildString(releaseDateTypes)
  }

  override fun isCalculationInitialised(): Boolean {
    return this::sentenceCalculation.isInitialized
  }
  fun combinedDuration(): Duration {
    val firstSentence = standardSentences.get(0)
    val secondSentence = standardSentences.get(1)
    val durationElements: MutableMap<ChronoUnit, Long> = mutableMapOf()
    durationElements[ChronoUnit.DAYS] = ChronoUnit.DAYS.between(
      earliestSentencedAt(firstSentence, secondSentence),
      latestExpiryDate(firstSentence, secondSentence)?.plusDays(1L)
    )
    val duration = Duration(durationElements)
    if (duration.getEndDate(firstSentence.sentencedAt).isAfter(earliestSentencedAt(firstSentence, secondSentence).plusYears(2))) {
      durationElements[ChronoUnit.DAYS] = ChronoUnit.DAYS.between(
        earliestSentencedAt(firstSentence, secondSentence),
        earliestSentencedAt(firstSentence, secondSentence).plusYears(2)
      )
      return Duration(durationElements)
    }
    return duration
  }

  override fun getLengthInDays(): Int {
    return combinedDuration().getLengthInDays(sentencedAt)
  }

  override fun hasAnyEdsOrSopcSentence(): Boolean {
    return false
  }

  private fun earliestSentencedAt(firstStandardSentence: AbstractSentence, secondStandardSentence: AbstractSentence): LocalDate {
    return if (firstStandardSentence.sentencedAt.isBefore(secondStandardSentence.sentencedAt)) {
      firstStandardSentence.sentencedAt
    } else {
      secondStandardSentence.sentencedAt
    }
  }

  private fun latestExpiryDate(firstStandardSentence: AbstractSentence, secondStandardSentence: AbstractSentence): LocalDate? {
    return if (
      firstStandardSentence.sentenceCalculation.expiryDate.isAfter(secondStandardSentence.sentenceCalculation.expiryDate)
    ) {
      firstStandardSentence.sentenceCalculation.expiryDate
    } else {
      secondStandardSentence.sentenceCalculation.expiryDate
    }
  }

  override fun calculateErsedFromHalfway(): Boolean {
    return identificationTrack.calculateErsedFromHalfway()
  }
  override fun calculateErsedFromTwoThirds(): Boolean {
    return identificationTrack.calculateErsedFromTwoThirds()
  }
}