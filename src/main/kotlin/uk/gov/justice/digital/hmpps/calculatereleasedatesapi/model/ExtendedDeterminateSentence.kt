package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import java.time.LocalDate
import java.util.UUID

data class ExtendedDeterminateSentence(
  override val offence: Offence,
  val custodialDuration: Duration,
  val extensionDuration: Duration,
  override val sentencedAt: LocalDate,
  override val identifier: UUID = UUID.randomUUID(),
  override val consecutiveSentenceUUIDs: List<UUID> = listOf(),
  override val caseSequence: Int? = null,
  override val lineSequence: Int? = null,
  override val caseReference: String?,
  override val recallType: RecallType? = null
) : AbstractSentence(offence, sentencedAt, identifier, consecutiveSentenceUUIDs, caseSequence, lineSequence, caseReference, recallType) {

  override fun buildString(): String {
    return "Sentence\t:\t\n" +
      "Identification Track\t:\t${identificationTrack}\n" +
      "Custodial duration\t:\t${custodialDuration}\n" +
      "${custodialDuration.toPeriodString(sentencedAt)}\n" +
      "Extension duration\t:\t${extensionDuration}\n" +
      "${extensionDuration.toPeriodString(getStartOfExtension())}\n" +
      "Sentence Types\t:\t$recallType\n" +
      "Release Date Types\t:\t$releaseDateTypes\n" +
      "Number of Days in Sentence\t:\t${getLengthInDays()}\n" +
      sentenceCalculation.buildString(releaseDateTypes)
  }

  fun getCustodialLengthInDays(): Int {
    return custodialDuration.getLengthInDays(sentencedAt)
  }

  fun getExtensionLengthInDays(): Int {
    return extensionDuration.getLengthInDays(getStartOfExtension())
  }

  fun getStartOfExtension(): LocalDate {
    return sentencedAt.plusDays(getCustodialLengthInDays().toLong()).plusDays(1)
  }

  override fun getLengthInDays(): Int {
    return getCustodialLengthInDays() + getExtensionLengthInDays()
  }
}
