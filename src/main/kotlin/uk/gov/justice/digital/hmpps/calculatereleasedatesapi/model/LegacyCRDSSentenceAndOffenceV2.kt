package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.OffenderOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceTerms
import java.math.BigDecimal
import java.time.LocalDate

// This version of sentence and offence data added indicators for SDS+ and would be SDS plus if sentenced today but maintained the multiple offence mapping.
@Deprecated("Maintained for backwards compatibility with historical calculations. Superseded by LegacyCRDSSentenceAndOffenceV3.")
data class LegacyCRDSSentenceAndOffenceV2(
  val bookingId: Long,
  val sentenceSequence: Int,
  val lineSequence: Int,
  val caseSequence: Int,
  val consecutiveToSequence: Int?,
  val sentenceStatus: String,
  val sentenceCategory: String,
  val sentenceCalculationType: String,
  val sentenceTypeDescription: String,
  val sentenceDate: LocalDate,
  val terms: List<SentenceTerms>,
  val offences: List<OffenderOffence>,
  val caseReference: String?,
  val courtId: String?,
  val courtDescription: String?,
  val courtTypeCode: String?,
  val fineAmount: BigDecimal?,
  val revocationDates: List<LocalDate> = emptyList(),
  val isSDSPlus: Boolean,
  val isSDSPlusEligibleSentenceTypeLengthAndOffence: Boolean,
) {
  fun toLatest(): List<SentenceAndOffenceWithReleaseArrangements> = offences.map {
    val sentenceType = SentenceCalculationType.from(sentenceCalculationType)
    SentenceAndOffenceWithReleaseArrangements(
      this.bookingId,
      this.sentenceSequence,
      this.lineSequence,
      this.caseSequence,
      this.consecutiveToSequence,
      this.sentenceStatus,
      this.sentenceCategory,
      this.sentenceCalculationType,
      this.sentenceTypeDescription,
      this.sentenceDate,
      this.terms,
      it,
      this.caseReference,
      this.courtId,
      this.courtDescription,
      this.courtTypeCode,
      this.fineAmount,
      this.revocationDates,
      if (sentenceType.isSDS()) {
        SDSReleaseArrangements(
          isSDSPlus = this.isSDSPlus,
          isSDSPlusEligibleSentenceTypeLengthAndOffence = this.isSDSPlusEligibleSentenceTypeLengthAndOffence,
          sdsEarlyReleaseExclusions = emptyList(),
          isSection250 = sentenceType.isSection250(),
        )
      } else {
        null
      },
    )
  }
}
