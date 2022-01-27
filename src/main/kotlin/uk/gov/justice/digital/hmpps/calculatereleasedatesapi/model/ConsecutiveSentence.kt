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

  override fun buildString(): String {
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

  private fun hasAfterCjaLaspo(): Boolean {
    return orderedSentences.any() { it.identificationTrack === SentenceIdentificationTrack.SDS_AFTER_CJA_LASPO }
  }

  private fun hasBeforeCjaLaspo(): Boolean {
    return orderedSentences.any() { it.identificationTrack === SentenceIdentificationTrack.SDS_BEFORE_CJA_LASPO }
  }

  fun hasOraSentences(): Boolean {
    return orderedSentences.any(Sentence::isOraSentence)
  }

  fun hasNonOraSentences(): Boolean {
    return orderedSentences.any { !it.isOraSentence() }
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

  private fun hasSdsPlusSentences(): Boolean {
    return orderedSentences.any(Sentence::isSdsSentence)
  }

  private fun hasSdsSentences(): Boolean {
    return orderedSentences.any { !it.isSdsSentence() }
  }

  fun isMadeUpOfSdsPlusAndSdsSentences(): Boolean {
    return hasSdsSentences() && hasSdsPlusSentences()
  }

  fun isMadeUpOfOnlySdsPlusSentences(): Boolean {
    return !hasSdsSentences() && hasSdsPlusSentences()
  }

}
