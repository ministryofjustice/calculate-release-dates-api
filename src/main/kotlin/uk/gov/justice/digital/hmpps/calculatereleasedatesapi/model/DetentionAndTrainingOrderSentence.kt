package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import java.time.LocalDate
import java.util.*

data class DetentionAndTrainingOrderSentence(
  override val offence: Offence,
  val duration: Duration,
  override val sentencedAt: LocalDate,
  override val identifier: UUID = UUID.randomUUID(),
  override val consecutiveSentenceUUIDs: List<UUID> = listOf(),
  override val caseSequence: Int? = null,
  override val lineSequence: Int? = null,
  override val caseReference: String? = null,
  override val recallType: RecallType? = null,
) : AbstractSentence(offence, sentencedAt, identifier, consecutiveSentenceUUIDs, caseSequence, lineSequence, caseReference, recallType) {
  override val isSDSPlus: Boolean = false
  override val isSDSPlusEligibleSentenceAndOffence: Boolean = false
  override fun getLengthInDays(): Int {
    return duration.getLengthInDays(this.sentencedAt)
  }

  override fun hasAnyEdsOrSopcSentence(): Boolean {
    return false
  }

  override fun buildString(): String {
    return "Sentence\t:\t\n" +
      "Identification Track\t:\t${identificationTrack}\n" +
      "Duration\t:\t$duration\n" +
      "${duration.toPeriodString(sentencedAt)}\n" +
      "Sentence Types\t:\t$recallType\n" +
      "Release Date Types\t:\t$releaseDateTypes\n" +
      "Number of Days in Sentence\t:\t${getLengthInDays()}\n" +
      sentenceCalculation.buildString(releaseDateTypes.initialTypes)
  }
}
