package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import java.time.LocalDate
import java.util.UUID

data class IndeterminateSentence(
  override val offence: Offence,
  override val sentencedAt: LocalDate,
  override val identifier: UUID = UUID.randomUUID(),
  override val consecutiveSentenceUUIDs: List<UUID> = listOf(),
  override val caseSequence: Int? = null,
  override val lineSequence: Int? = null,
) : AbstractSentence(offence, sentencedAt, identifier, consecutiveSentenceUUIDs, caseSequence, lineSequence),
  Term {
  override val isSDSPlus: Boolean = false
  override val isSDSPlusEligibleSentenceTypeLengthAndOffence: Boolean = false
  override val isSDSPlusOffenceInPeriod: Boolean = false
  override fun getLengthInDays(): Int {
    return -1
  }

  override fun buildString(): String = "Indeterminate Sentence\t:\t\n" +
    "Release Date Types\t:\t$releaseDateTypes\n" +
    "Number of Days in Sentence\t:\t${getLengthInDays()}\n" +
    sentenceCalculation.buildString(releaseDateTypes.initialTypes)

  override fun hasAnyEdsOrSopcSentence(): Boolean {
    return false
  }
}
