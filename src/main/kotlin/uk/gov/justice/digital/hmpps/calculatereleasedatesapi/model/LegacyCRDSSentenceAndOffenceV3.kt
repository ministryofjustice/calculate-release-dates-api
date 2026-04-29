package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.OffenderOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAndOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceTerms
import java.math.BigDecimal
import java.time.LocalDate

// This version of sentence and offence data added SDS early release exclusions to support SDS40 and also removed multiple offences to a single sentence with that now being handled upstream
@Deprecated("Maintained for backwards compatibility with historical calculations. Superseded by SentenceAndOffenceWithReleaseArrangements")
data class LegacyCRDSSentenceAndOffenceV3(
  override val bookingId: Long,
  override val sentenceSequence: Int,
  override val lineSequence: Int,
  override val caseSequence: Int,
  override val consecutiveToSequence: Int?,
  override val sentenceStatus: String,
  override val sentenceCategory: String,
  override val sentenceCalculationType: String,
  override val sentenceTypeDescription: String,
  override val sentenceDate: LocalDate,
  override val terms: List<SentenceTerms>,
  override val offence: OffenderOffence,
  override val caseReference: String?,
  override val courtId: String?,
  override val courtDescription: String?,
  override val courtTypeCode: String?,
  override val fineAmount: BigDecimal?,
  override val revocationDates: List<LocalDate> = emptyList(),
  val isSDSPlus: Boolean,
  val isSDSPlusEligibleSentenceTypeLengthAndOffence: Boolean,
  val hasAnSDSEarlyReleaseExclusion: SDSEarlyReleaseExclusionType,
) : SentenceAndOffence {

  fun toLatest(): SentenceAndOffenceWithReleaseArrangements {
    val sentenceType = SentenceCalculationType.from(sentenceCalculationType)
    return SentenceAndOffenceWithReleaseArrangements(
      bookingId = bookingId,
      sentenceSequence = sentenceSequence,
      lineSequence = lineSequence,
      caseSequence = caseSequence,
      consecutiveToSequence = consecutiveToSequence,
      sentenceStatus = sentenceStatus,
      sentenceCategory = sentenceCategory,
      sentenceCalculationType = sentenceCalculationType,
      sentenceTypeDescription = sentenceTypeDescription,
      sentenceDate = sentenceDate,
      terms = terms,
      offence = offence,
      caseReference = caseReference,
      courtId = courtId,
      courtDescription = courtDescription,
      courtTypeCode = courtTypeCode,
      fineAmount = fineAmount,
      revocationDates = revocationDates,
      sdsReleaseArrangements = if (sentenceType.isSDS()) {
        SDSReleaseArrangements(
          isSDSPlus = this.isSDSPlus,
          isSDSPlusEligibleSentenceTypeLengthAndOffence = this.isSDSPlusEligibleSentenceTypeLengthAndOffence,
          sdsEarlyReleaseExclusions = if (hasAnSDSEarlyReleaseExclusion == SDSEarlyReleaseExclusionType.NO) emptyList() else listOf(hasAnSDSEarlyReleaseExclusion),
          isSection250 = sentenceType.isSection250(),
        )
      } else {
        null
      },
    )
  }
}
