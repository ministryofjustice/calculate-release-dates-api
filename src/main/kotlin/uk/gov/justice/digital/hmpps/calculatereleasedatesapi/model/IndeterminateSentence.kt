package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.HistoricalTusedSource
import java.time.LocalDate
import java.util.UUID

data class IndeterminateSentence(
  override val offence: Offence,
  val duration: Duration,
  override val sentencedAt: LocalDate,
  override val identifier: UUID = UUID.randomUUID(),
  override val consecutiveSentenceUUIDs: List<UUID> = listOf(),
  override val caseSequence: Int? = null,
  override val lineSequence: Int? = null,
  var latestTusedDate: LocalDate? = null,
  var latestTusedSource: HistoricalTusedSource? = null,
) : AbstractSentence(offence, sentencedAt, identifier, consecutiveSentenceUUIDs, caseSequence, lineSequence),
  Term {
  override val isSDSPlus: Boolean = false
  override val isSDSPlusEligibleSentenceTypeLengthAndOffence: Boolean = false
  override val isSDSPlusOffenceInPeriod: Boolean = false

  override fun buildString(): String = "Indeterminate Sentence\t:\t\n" +
    "${duration.toPeriodString(sentencedAt)}\n" +
    "Release Date Types\t:\t$releaseDateTypes\n" +
    "Number of Days in Sentence\t:\t${getLengthInDays()}\n" +
    sentenceCalculation.buildString(releaseDateTypes.initialTypes)

  override fun getLengthInDays(): Int = duration.getLengthInDays(this.sentencedAt)

  override fun hasAnyEdsOrSopcSentence(): Boolean {
    return false
  }
}
