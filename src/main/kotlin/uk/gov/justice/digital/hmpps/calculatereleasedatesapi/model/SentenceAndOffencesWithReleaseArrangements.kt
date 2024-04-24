package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.OffenderOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAndOffences
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceTerms
import java.math.BigDecimal
import java.time.LocalDate

data class SentenceAndOffencesWithReleaseArrangements(
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
  override val offences: List<OffenderOffence>,
  override val caseReference: String?,
  override val courtDescription: String?,
  override val fineAmount: BigDecimal?,
  val isSdsPlus: Boolean,
) : SentenceAndOffences {
  constructor(source: SentenceAndOffences, isSdsPlus: Boolean) : this(
    source.bookingId,
    source.sentenceSequence,
    source.lineSequence,
    source.caseSequence,
    source.consecutiveToSequence,
    source.sentenceStatus,
    source.sentenceCategory,
    source.sentenceCalculationType,
    source.sentenceTypeDescription,
    source.sentenceDate,
    source.terms,
    source.offences,
    source.caseReference,
    source.courtDescription,
    source.fineAmount,
    isSdsPlus,
  )
}
