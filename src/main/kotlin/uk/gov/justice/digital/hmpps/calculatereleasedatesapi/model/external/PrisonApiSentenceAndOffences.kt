package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SDSReleaseArrangements
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceAndOffenceWithReleaseArrangements
import java.math.BigDecimal
import java.time.LocalDate

data class PrisonApiSentenceAndOffences(
  val bookingId: Long,
  val sentenceSequence: Int,
  val lineSequence: Int,
  val caseSequence: Int,
  val consecutiveToSequence: Int? = null,
  val sentenceStatus: String,
  val sentenceCategory: String,
  val sentenceCalculationType: String,
  val sentenceTypeDescription: String,
  val sentenceDate: LocalDate,
  val terms: List<SentenceTerms> = emptyList(),
  val offences: List<OffenderOffence> = emptyList(),
  val caseReference: String? = null,
  val courtId: String? = null,
  val courtDescription: String? = null,
  val courtTypeCode: String? = null,
  val fineAmount: BigDecimal? = null,
  val revocationDates: List<LocalDate> = emptyList(),
) {
  fun toLatest(): List<SentenceAndOffenceWithReleaseArrangements> = offences.map { offence ->
    SentenceAndOffenceWithReleaseArrangements(
      bookingId = this.bookingId,
      sentenceSequence = this.sentenceSequence,
      lineSequence = this.lineSequence,
      caseSequence = this.caseSequence,
      consecutiveToSequence = this.consecutiveToSequence,
      sentenceStatus = this.sentenceStatus,
      sentenceCategory = this.sentenceCategory,
      sentenceCalculationType = this.sentenceCalculationType,
      sentenceTypeDescription = this.sentenceTypeDescription,
      sentenceDate = this.sentenceDate,
      terms = this.terms,
      offence = offence,
      caseReference = this.caseReference,
      courtId = this.courtId,
      courtDescription = this.courtDescription,
      courtTypeCode = this.courtTypeCode,
      fineAmount = this.fineAmount,
      revocationDates = this.revocationDates,
      sdsReleaseArrangements = if (SentenceCalculationType.from(this.sentenceCalculationType).isSDS()) {
        SDSReleaseArrangements(
          isSDSPlus = false,
          isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
          sdsEarlyReleaseExclusions = emptyList(),
          isSection250 = SentenceCalculationType.from(this.sentenceCalculationType).isSection250(),
        )
      } else {
        null
      },
    )
  }
}
