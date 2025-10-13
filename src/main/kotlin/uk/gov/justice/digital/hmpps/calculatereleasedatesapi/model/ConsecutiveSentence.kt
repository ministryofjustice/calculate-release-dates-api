package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import com.fasterxml.jackson.annotation.JsonIgnore
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceIdentificationTrack
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class ConsecutiveSentence(val orderedSentences: List<AbstractSentence>) : CalculableSentence {
  override val sentencedAt: LocalDate = orderedSentences.minOf(CalculableSentence::sentencedAt)
  override val offence: Offence = orderedSentences.map(CalculableSentence::offence).filter { it.committedAt != null }.minByOrNull { it.committedAt!! } ?: orderedSentences[0].offence
  override val isSDSPlus: Boolean = orderedSentences.all { it.isSDSPlus }
  override val isSDSPlusEligibleSentenceTypeLengthAndOffence: Boolean = orderedSentences.all { it.isSDSPlusEligibleSentenceTypeLengthAndOffence }
  override val isSDSPlusOffenceInPeriod: Boolean = orderedSentences.all { it.isSDSPlusOffenceInPeriod }

  override val recall: Recall?
    get() {
      return orderedSentences[0].recall
    }

  @JsonIgnore
  override lateinit var sentenceCalculation: SentenceCalculation

  @JsonIgnore
  override lateinit var identificationTrack: SentenceIdentificationTrack

  @JsonIgnore
  override lateinit var releaseDateTypes: ReleaseDateTypes

  override fun buildString(): String = "StandardDeterminateConsecutiveSentence\t:\t\n" +
    "Number of sentences\t:\t${orderedSentences.size}\n" +
    "Sentence Types\t:\t$releaseDateTypes\n" +
    "Number of Days in Sentence\t:\t${getLengthInDays()}\n" +
    sentenceCalculation.buildString(releaseDateTypes.initialTypes)

  override fun isCalculationInitialised(): Boolean = this::sentenceCalculation.isInitialized

  fun getCombinedDuration(): Duration = this.orderedSentences.map {
    when (it) {
      is StandardDeterminateSentence -> {
        it.duration
      }

      is ExtendedDeterminateSentence -> {
        it.combinedDuration()
      }

      is SopcSentence -> {
        it.combinedDuration()
      }

      is DetentionAndTrainingOrderSentence -> {
        it.duration
      }

      is BotusSentence -> {
        it.duration
      }

      is AFineSentence -> {
        it.duration
      }

      else -> {
        throw UnsupportedOperationException("Unknown type of sentence in a consecutive sentence ${it.javaClass}")
      }
    }
  }.reduce { acc, duration -> acc.appendAll(duration.durationElements) }

  override fun getLengthInDays(): Int {
    val duration = getCombinedDuration()
    if (isMadeUpOfOnlyDtos() && isMoreThanTwoYears(duration)) {
      val map: MutableMap<ChronoUnit, Long> = mutableMapOf()
      map[ChronoUnit.MONTHS] = 24
      return Duration(map.toMap()).getLengthInDays(sentencedAt)
    }
    return duration.getLengthInDays(sentencedAt)
  }

  private fun isMoreThanTwoYears(duration: Duration) = duration.getLengthInDays(sentencedAt) > ChronoUnit.DAYS.between(this.sentencedAt, this.sentencedAt.plus(24, ChronoUnit.MONTHS))

  override fun hasAnyEdsOrSopcSentence(): Boolean = hasExtendedSentence() || hasSopcSentence()

  fun allSentencesAreStandardSentences(): Boolean = orderedSentences.all { it is StandardDeterminateSentence }

  fun hasExtendedSentence(): Boolean = orderedSentences.any { it is ExtendedDeterminateSentence }

  fun hasSopcSentence(): Boolean = orderedSentences.any { it is SopcSentence }

  private fun hasAfterCjaLaspo(): Boolean = orderedSentences.any { it is StandardDeterminateSentence && it.isAfterCJAAndLASPO() }

  private fun hasBeforeCjaLaspo(): Boolean = orderedSentences.any { it is StandardDeterminateSentence && it.isBeforeCJAAndLASPO() }

  fun hasOraSentences(): Boolean = orderedSentences.any { it is StandardDeterminateSentence && it.isOraSentence() }

  fun hasNonOraSentences(): Boolean = orderedSentences.any { it is StandardDeterminateSentence && !it.isOraSentence() }

  fun isMadeUpOfBeforeAndAfterCjaLaspoSentences(): Boolean = hasBeforeCjaLaspo() && hasAfterCjaLaspo()

  fun isMadeUpOfOnlyBeforeCjaLaspoSentences(): Boolean = hasBeforeCjaLaspo() && !hasAfterCjaLaspo()

  fun isMadeUpOfOnlyAfterCjaLaspoSentences(): Boolean = hasAfterCjaLaspo() && !hasBeforeCjaLaspo()

  fun isMadeUpOfOnlySdsPlusSentences(): Boolean = orderedSentences.all { it is StandardDeterminateSentence && it.isSDSPlus }

  fun hasDiscretionaryRelease(): Boolean = orderedSentences.any { it is ExtendedDeterminateSentence && !it.automaticRelease }

  override fun calculateErsed(): Boolean = orderedSentences.any { it.identificationTrack.calculateErsed() }

  fun isMadeUpOfOnlyDtos(): Boolean = orderedSentences.all { it is DetentionAndTrainingOrderSentence }

  override fun isIdentificationTrackInitialized(): Boolean = this::identificationTrack.isInitialized

  override fun isOrExclusivelyBotus(): Boolean = orderedSentences.all { it.isOrExclusivelyBotus() }

  @JsonIgnore
  override fun sentenceParts(): List<AbstractSentence> = orderedSentences
}
