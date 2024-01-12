package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import com.fasterxml.jackson.annotation.JsonIgnore
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceIdentificationTrack
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class ConsecutiveSentence(val orderedSentences: List<CalculableSentence>) : CalculableSentence {
  override val sentencedAt: LocalDate = orderedSentences.minOf(CalculableSentence::sentencedAt)
  override val offence: Offence = orderedSentences.map(CalculableSentence::offence).minByOrNull(Offence::committedAt)!!

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
      sentenceCalculation.buildString(releaseDateTypes)
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

  fun allSentencesAreExtendedSentences(): Boolean {
    return orderedSentences.all { it is ExtendedDeterminateSentence }
  }

  fun hasExtendedSentence(): Boolean {
    return orderedSentences.any { it is ExtendedDeterminateSentence }
  }

  fun hasSopcSentence(): Boolean {
    return orderedSentences.any { it is SopcSentence }
  }

  private fun hasAfterCjaLaspo(): Boolean {
    return orderedSentences.any { it.identificationTrack === SentenceIdentificationTrack.SDS_AFTER_CJA_LASPO }
  }

  private fun hasBeforeCjaLaspo(): Boolean {
    return orderedSentences.any() { it.identificationTrack === SentenceIdentificationTrack.SDS_BEFORE_CJA_LASPO }
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

  private fun hasSdsTwoThirdsReleaseSentence(): Boolean {
    return orderedSentences.any { it is StandardDeterminateSentence && it.isTwoThirdsReleaseSentence() }
  }

  private fun hasSdsHalfwayReleaseSentence(): Boolean {
    return orderedSentences.any { it is StandardDeterminateSentence && !it.isTwoThirdsReleaseSentence() }
  }

  fun isMadeUpOfSdsHalfwayReleaseAndTwoThirdsReleaseSentence(): Boolean {
    return hasSdsHalfwayReleaseSentence() && hasSdsTwoThirdsReleaseSentence()
  }

  fun isMadeUpOfOnlySdsTwoThirdsReleaseSentences(): Boolean {
    return !hasSdsHalfwayReleaseSentence() && hasSdsTwoThirdsReleaseSentence()
  }

  fun hasAutomaticRelease(): Boolean {
    return orderedSentences.any { it is ExtendedDeterminateSentence && it.automaticRelease }
  }

  fun hasDiscretionaryRelease(): Boolean {
    return orderedSentences.any { it is ExtendedDeterminateSentence && !it.automaticRelease }
  }

  fun hasAutomaticAndDiscretionaryRelease(): Boolean {
    return hasAutomaticRelease() && hasDiscretionaryRelease()
  }

  fun getAutomaticReleaseCustodialLengthInDays(): Int {
    return orderedSentences
      .filter { it is ExtendedDeterminateSentence && it.automaticRelease }
      .map { (it as ExtendedDeterminateSentence).custodialDuration }
      .reduce { acc, it -> acc.appendAll(it.durationElements) }
      .getLengthInDays(sentencedAt)
  }

  fun getDiscretionaryReleaseCustodialLengthInDays(startDate: LocalDate): Int {
    return orderedSentences
      .filter { it is ExtendedDeterminateSentence && !it.automaticRelease }
      .map { (it as ExtendedDeterminateSentence).custodialDuration }
      .reduce { acc, it -> acc.appendAll(it.durationElements) }
      .getLengthInDays(startDate)
  }

  override fun calculateErsedFromHalfway(): Boolean {
    return orderedSentences.all { it.identificationTrack.calculateErsedFromHalfway() }
  }

  override fun calculateErsedFromTwoThirds(): Boolean {
    return orderedSentences.all { it.identificationTrack.calculateErsedFromTwoThirds() }
  }

  override fun calulateErsedMixed(): Boolean {
    return orderedSentences.any { it.identificationTrack.calculateErsedFromHalfway() } &&
      orderedSentences.any { it.identificationTrack.calculateErsedFromTwoThirds() }
  }

  fun isMadeUpOfOnlyDtos(): Boolean {
    return orderedSentences.all { it is DetentionAndTrainingOrderSentence }
  }

  override fun isIdentificationTrackInitialized(): Boolean {
    return this::identificationTrack.isInitialized
  }

  fun isMadeUpOfSdsAndSdsPlusSentences(): Boolean {
    return (orderedSentences.any { it.offence.isPcscSdsPlus } || orderedSentences.any { it.offence.isPcscSds }) && orderedSentences.any { !it.offence.isPcscSdsPlus }
  }
}
