package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import com.fasterxml.jackson.annotation.JsonIgnore
import java.time.LocalDate
import java.util.*

/**
 * This class is used to model the SOPC Sentences
 */
data class SopcSentence(
  override val offence: Offence,
  val custodialDuration: Duration,
  val extensionDuration: Duration,
  val sdopcu18: Boolean = false,
  override val sentencedAt: LocalDate,
  override val identifier: UUID = UUID.randomUUID(),
  override val consecutiveSentenceUUIDs: List<UUID> = listOf(),
  override val caseSequence: Int? = null,
  override val lineSequence: Int? = null,
  override val externalSentenceId: ExternalSentenceId? = null,
  override val caseReference: String?,
  override val recall: Recall? = null,
) : AbstractSentence(offence, sentencedAt, identifier, consecutiveSentenceUUIDs, caseSequence, lineSequence, externalSentenceId, caseReference, recall) {
  override val isSDSPlus: Boolean = false
  override val isSDSPlusEligibleSentenceTypeLengthAndOffence: Boolean = false
  override val isSDSPlusOffenceInPeriod: Boolean = false

  override fun buildString(): String = "SopcSentence\t:\t\n" +
    "Identification Track\t:\t${identificationTrack}\n" +
    "Custodial duration\t:\t${custodialDuration}\n" +
    "Extension duration\t:\t${extensionDuration}\n" +
    "Sentence Types\t:\t$recallType\n" +
    "Release Date Types\t:\t$releaseDateTypes\n" +
    "Number of Days in Sentence\t:\t${getLengthInDays()}\n" +
    sentenceCalculation.buildString(releaseDateTypes.initialTypes)

  @JsonIgnore
  fun getCustodialLengthInDays(): Int = custodialDuration.getLengthInDays(sentencedAt)

  fun combinedDuration(): Duration = custodialDuration.appendAll(extensionDuration.durationElements)
  override fun getLengthInDays(): Int = combinedDuration().getLengthInDays(sentencedAt)

  override fun hasAnyEdsOrSopcSentence(): Boolean = true
}
