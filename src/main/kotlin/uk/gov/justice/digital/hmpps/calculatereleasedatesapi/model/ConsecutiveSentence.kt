package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import com.fasterxml.jackson.annotation.JsonIgnore
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceIdentificationTrack
import java.lang.UnsupportedOperationException
import java.time.LocalDate

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
  override lateinit var releaseDateTypes: List<ReleaseDateType>

  override fun buildString(): String {
    return "StandardDeterminateConsecutiveSentence\t:\t\n" +
      "Number of sentences\t:\t${orderedSentences.size}\n" +
      "Sentence Types\t:\t$releaseDateTypes\n" +
      "Number of Days in Sentence\t:\t${getLengthInDays()}\n" +
      sentenceCalculation.buildString(releaseDateTypes)
  }

  fun getCombinedDuration(): Duration {
    return this.orderedSentences.map {
      when (it) {
        is StandardDeterminateSentence -> {
          it.duration
        }
        is ExtendedDeterminateSentence -> {
          it.custodialDuration.appendAll(it.extensionDuration.durationElements)
        }
        else -> {
          throw UnsupportedOperationException("Unknown type of sentence in a consecutive sentence ${it.javaClass}")
        }
      }
    }.reduce { acc, duration -> acc.appendAll(duration.durationElements) }
  }
  override fun getLengthInDays(): Int {
    val duration = getCombinedDuration()
    return duration.getLengthInDays(sentencedAt)
  }

  override fun hasAnyEdsSentence(): Boolean {
    return hasExtendedSentence()
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
}
