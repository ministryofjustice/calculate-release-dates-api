package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import com.fasterxml.jackson.annotation.JsonIgnore
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceIdentificationTrack
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class DtoSingleTermSentence(
  override val sentencedAt: LocalDate,
  override val offence: Offence,
  override val standardSentences: List<AbstractSentence>,
  val sentences: List<CalculableSentence>,
) : SingleTermed, Term {
  override val isSDSPlus: Boolean = false
  override val isSDSPlusEligibleSentenceTypeLengthAndOffence: Boolean = false
  override val isSDSPlusOffenceInPeriod: Boolean = false
  constructor(sentences: List<CalculableSentence>) :
    this(
      sentences.minOf(CalculableSentence::sentencedAt),
      sentences.map(CalculableSentence::offence).minByOrNull(Offence::committedAt)!!,
      sentences.flatMap { it.sentenceParts() },
      sentences,
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
      "Number of sentences\t:\t${sentences.size}\n" +
      "Sentence Types\t:\t$releaseDateTypes\n" +
      "Number of Days in Sentence\t:\t${getLengthInDays()}\n" +
      sentenceCalculation.buildString(releaseDateTypes.initialTypes)
  }

  override fun isCalculationInitialised(): Boolean {
    return this::sentenceCalculation.isInitialized
  }

  override fun isIdentificationTrackInitialized(): Boolean {
    return this::identificationTrack.isInitialized
  }

  fun combinedDuration(): Duration {
    val sentencesOrderedByLengthThenSentenceDate = sentences.sortedWith(compareBy({ it.getLengthInDays() }, { it.sentencedAt }))
    val longestEarliestSentence = sentencesOrderedByLengthThenSentenceDate.first()
    val shortestRecentSentence = sentencesOrderedByLengthThenSentenceDate.last()
    val durationElements: MutableMap<ChronoUnit, Long> = mutableMapOf()
    durationElements[ChronoUnit.DAYS] = ChronoUnit.DAYS.between(
      earliestSentencedAt(longestEarliestSentence, shortestRecentSentence),
      latestExpiryDate(longestEarliestSentence, shortestRecentSentence)?.plusDays(1L),
    )
    val duration = Duration(durationElements)
    if (duration.getEndDate(longestEarliestSentence.sentencedAt).isAfter(earliestSentencedAt(longestEarliestSentence, shortestRecentSentence).plusYears(2))) {
      durationElements[ChronoUnit.DAYS] = ChronoUnit.DAYS.between(
        earliestSentencedAt(longestEarliestSentence, shortestRecentSentence),
        earliestSentencedAt(longestEarliestSentence, shortestRecentSentence).plusYears(2),
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

  private fun earliestSentencedAt(firstStandardSentence: CalculableSentence, secondStandardSentence: CalculableSentence): LocalDate {
    return if (firstStandardSentence.sentencedAt.isBefore(secondStandardSentence.sentencedAt)) {
      firstStandardSentence.sentencedAt
    } else {
      secondStandardSentence.sentencedAt
    }
  }

  private fun latestExpiryDate(firstStandardSentence: CalculableSentence, secondStandardSentence: CalculableSentence): LocalDate? {
    val firstExpiry = firstStandardSentence.totalDuration().getEndDate(firstStandardSentence.sentencedAt)
    val secondExpiry = secondStandardSentence.totalDuration().getEndDate(secondStandardSentence.sentencedAt)

    return if (
      firstExpiry.isAfter(secondExpiry)
    ) {
      firstExpiry
    } else {
      secondExpiry
    }
  }

  override fun calculateErsed(): Boolean = identificationTrack.calculateErsed()

  @JsonIgnore
  override fun sentenceParts(): List<AbstractSentence> {
    return standardSentences
  }
}
