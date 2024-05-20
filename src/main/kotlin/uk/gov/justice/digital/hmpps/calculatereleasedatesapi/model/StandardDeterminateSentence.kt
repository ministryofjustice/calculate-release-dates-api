package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import com.fasterxml.jackson.annotation.JsonIgnore
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceIdentificationTrack
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
  override val caseReference: String? = null,
  override val recallType: RecallType? = null,
  override val isSDSPlus: Boolean,
  val hasAnSDSEarlyReleaseExclusion: SDSEarlyReleaseExclusionType,
) : AbstractSentence(offence, sentencedAt, identifier, consecutiveSentenceUUIDs, caseSequence, lineSequence, caseReference, recallType) {

  @JsonIgnore
  val releaseArrangements: List<SentenceIdentificationTrack> = if (isSDSPlus) {
    listOf(SentenceIdentificationTrack.SDS_PLUS_RELEASE)
  } else {
    if (hasAnSDSEarlyReleaseExclusion == SDSEarlyReleaseExclusionType.NO) {
      listOf(SentenceIdentificationTrack.SDS_EARLY_RELEASE, SentenceIdentificationTrack.SDS_STANDARD_RELEASE)
    } else {
      listOf(SentenceIdentificationTrack.SDS_STANDARD_RELEASE, SentenceIdentificationTrack.SDS_EARLY_RELEASE)
    }
  }

  @JsonIgnore
  val releaseArrangementCalculations: MutableMap<SentenceIdentificationTrack, SentenceCalculation> = mutableMapOf()

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

  override fun getLengthInDays(): Int {
    return duration.getLengthInDays(this.sentencedAt)
  }

  override fun hasAnyEdsOrSopcSentence(): Boolean {
    return false
  }

  @JsonIgnore
  fun isOraSentence(): Boolean {
    return offence.committedAt.isAfterOrEqualTo(ImportantDates.ORA_DATE)
  }

  @JsonIgnore
  fun isBeforeCJAAndLASPO(): Boolean {
    return sentencedAt.isBefore(LASPO_DATE) && offence.committedAt.isBefore(CJA_DATE)
  }

  @JsonIgnore
  fun isAfterCJAAndLASPO(): Boolean {
    return !isBeforeCJAAndLASPO()
  }
}
