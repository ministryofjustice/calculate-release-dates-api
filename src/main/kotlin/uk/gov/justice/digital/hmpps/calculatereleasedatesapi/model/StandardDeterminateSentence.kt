package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import com.fasterxml.jackson.annotation.JsonIgnore
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.ImportantDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.ImportantDates.CJA_DATE
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.ImportantDates.LASPO_DATE
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.isAfterOrEqualTo
import java.time.LocalDate
import java.util.*

data class StandardDeterminateSentence(
  override val offence: Offence,
  val duration: Duration,
  override val sentencedAt: LocalDate,
  override val identifier: UUID = UUID.randomUUID(),
  override val consecutiveSentenceUUIDs: List<UUID> = listOf(),
  override val caseSequence: Int? = null,
  override val lineSequence: Int? = null,
  override val externalSentenceId: ExternalSentenceId? = null,
  override val caseReference: String? = null,
  override val recall: Recall? = null,
  override val isSDSPlus: Boolean,
  override val isSDSPlusEligibleSentenceTypeLengthAndOffence: Boolean = isSDSPlus,
  override val isSDSPlusOffenceInPeriod: Boolean = isSDSPlus,
  val hasAnSDSEarlyReleaseExclusion: SDSEarlyReleaseExclusionType,
  val section250: Boolean = false,
) : AbstractSentence(offence, sentencedAt, identifier, consecutiveSentenceUUIDs, caseSequence, lineSequence, externalSentenceId, caseReference, recall) {

  override fun buildString(): String = "Sentence\t:\t\n" +
    "Identification Track\t:\t${identificationTrack}\n" +
    "Duration\t:\t$duration\n" +
    "${duration.toPeriodString(sentencedAt)}\n" +
    "Sentence Types\t:\t$recallType\n" +
    "Release Date Types\t:\t$releaseDateTypes\n" +
    "Number of Days in Sentence\t:\t${getLengthInDays()}\n" +
    sentenceCalculation.buildString(releaseDateTypes.initialTypes)

  override fun getLengthInDays(): Int = duration.getLengthInDays(this.sentencedAt)

  override fun hasAnyEdsOrSopcSentence(): Boolean = false

  @JsonIgnore
  fun isOraSentence(): Boolean = offence.committedAt?.isAfterOrEqualTo(ImportantDates.ORA_DATE) == true

  @JsonIgnore
  fun isBeforeCJAAndLASPO(): Boolean = sentencedAt.isBefore(LASPO_DATE) && offence.committedAt?.isBefore(CJA_DATE) == true

  @JsonIgnore
  fun isAfterCJAAndLASPO(): Boolean = !isBeforeCJAAndLASPO()
}
