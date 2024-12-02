package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import com.fasterxml.jackson.annotation.JsonIgnore
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceIdentificationTrack
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class ConsecutiveSentence(val orderedSentences: List<AbstractSentence>) : CalculableSentence {
  override val sentencedAt: LocalDate = orderedSentences.minOf(CalculableSentence::sentencedAt)
  override val offence: Offence = orderedSentences.map(CalculableSentence::offence).minByOrNull(Offence::committedAt)!!
  override val isSDSPlus: Boolean = orderedSentences.all { it.isSDSPlus }
  override val isSDSPlusEligibleSentenceAndOffence: Boolean = orderedSentences.all { it.isSDSPlusEligibleSentenceAndOffence }

  override val recallType: RecallType?
    get() {
      return orderedSentences[0].recallType
    }

  @JsonIgnore
  override lateinit var sentenceCalculation: SentenceCalculation

  @JsonIgnore
  override lateinit var identificationTrack: SentenceIdentificationTrack

  @JsonIgnore
  override lateinit var releaseDateTypes: ReleaseDateTypes

  override fun buildString(): String {
    return "StandardDeterminateConsecutiveSentence\t:\t\n" +
      "Number of sentences\t:\t${orderedSentences.size}\n" +
      "Sentence Types\t:\t$releaseDateTypes\n" +
      "Number of Days in Sentence\t:\t${getLengthInDays()}\n" +
      sentenceCalculation.buildString(releaseDateTypes.initialTypes)
  }

  override fun isCalculationInitialised(): Boolean {
    return this::sentenceCalculation.isInitialized
  }

  fun getCombinedDuration(): Duration {
    return this.orderedSentences.map {
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
  }

  override fun getLengthInDays(): Int {
    val duration = getCombinedDuration()
    if (isMadeUpOfOnlyDtos() && isMoreThanTwoYears(duration)) {
      val map: MutableMap<ChronoUnit, Long> = mutableMapOf()
      map[ChronoUnit.MONTHS] = 24
      return Duration(map.toMap()).getLengthInDays(sentencedAt)
    }
    return duration.getLengthInDays(sentencedAt)
  }

  private fun isMoreThanTwoYears(duration: Duration) =
    duration.getLengthInDays(sentencedAt) > ChronoUnit.DAYS.between(this.sentencedAt, this.sentencedAt.plus(24, ChronoUnit.MONTHS))

  override fun hasAnyEdsOrSopcSentence(): Boolean {
    return hasExtendedSentence() || hasSopcSentence()
  }

  fun allSentencesAreStandardSentences(): Boolean {
    return orderedSentences.all { it is StandardDeterminateSentence }
  }

  fun hasExtendedSentence(): Boolean {
    return orderedSentences.any { it is ExtendedDeterminateSentence }
  }

  fun hasSopcSentence(): Boolean {
    return orderedSentences.any { it is SopcSentence }
  }

  private fun hasAfterCjaLaspo(): Boolean {
    return orderedSentences.any { it is StandardDeterminateSentence && it.isAfterCJAAndLASPO() }
  }

  private fun hasBeforeCjaLaspo(): Boolean {
    return orderedSentences.any { it is StandardDeterminateSentence && it.isBeforeCJAAndLASPO() }
  }

  fun hasOraSentences(): Boolean {
    return orderedSentences.any { it is StandardDeterminateSentence && it.isOraSentence() }
  }

  fun hasNonOraSentences(): Boolean {
    return orderedSentences.any { it is StandardDeterminateSentence && !it.isOraSentence() }
  }

  fun isMadeUpOfBeforeAndAfterCjaLaspoSentences(): Boolean {
    return hasBeforeCjaLaspo() && hasAfterCjaLaspo()
  }

  fun isMadeUpOfOnlyBeforeCjaLaspoSentences(): Boolean {
    return hasBeforeCjaLaspo() && !hasAfterCjaLaspo()
  }

  fun isMadeUpOfOnlyAfterCjaLaspoSentences(): Boolean {
    return hasAfterCjaLaspo() && !hasBeforeCjaLaspo()
  }

  fun isMadeUpOfOnlySdsPlusSentences(): Boolean {
    return orderedSentences.all { it is StandardDeterminateSentence && it.isSDSPlus }
  }

  fun hasDiscretionaryRelease(): Boolean {
    return orderedSentences.any { it is ExtendedDeterminateSentence && !it.automaticRelease }
  }

  override fun calculateErsed(): Boolean {
    return orderedSentences.any { it.identificationTrack.calculateErsed() }
  }

  fun isMadeUpOfOnlyDtos(): Boolean {
    return orderedSentences.all { it is DetentionAndTrainingOrderSentence }
  }

  override fun isIdentificationTrackInitialized(): Boolean {
    return this::identificationTrack.isInitialized
  }

  override fun isOrExclusivelyBotus(): Boolean {
    return orderedSentences.all { it.isOrExclusivelyBotus() }
  }

  @JsonIgnore
  override fun sentenceParts(): List<AbstractSentence> {
    return orderedSentences
  }
}
