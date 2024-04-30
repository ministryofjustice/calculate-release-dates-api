package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.OffenderOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceTerms
import java.math.BigDecimal
import java.time.LocalDate

data class SentenceAndOffencesWithSDSPlus(
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
  val courtDescription: String?,
  val fineAmount: BigDecimal?,
  val isSDSPlus: Boolean,
) {
  fun toLatest(): List<SentenceAndOffenceWithReleaseArrangements> = offences.map {
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
      this.courtDescription,
      this.fineAmount,
      this.isSDSPlus,
    )
  }
}
