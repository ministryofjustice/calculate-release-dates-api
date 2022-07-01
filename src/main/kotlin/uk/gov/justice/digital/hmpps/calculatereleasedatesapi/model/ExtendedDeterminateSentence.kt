package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import java.time.LocalDate
import java.util.UUID

/**
 * This class is used to model the Extended Determinate Sentences. This differ from standard sentences in that
 * they have two durations.
 */
data class ExtendedDeterminateSentence(
  override val offence: Offence,
  val custodialDuration: Duration,
  val extensionDuration: Duration,
  val automaticRelease: Boolean,
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
      "Extension duration\t:\t${extensionDuration}\n" +
      "Sentence Types\t:\t$recallType\n" +
      "Release Date Types\t:\t$releaseDateTypes\n" +
      "Number of Days in Sentence\t:\t${getLengthInDays()}\n" +
      sentenceCalculation.buildString(releaseDateTypes)
  }

  fun getCustodialLengthInDays(): Int {
    return custodialDuration.getLengthInDays(sentencedAt)
  }

  override fun getLengthInDays(): Int {
    return custodialDuration.appendAll(extensionDuration.durationElements).getLengthInDays(sentencedAt)
  }
}
