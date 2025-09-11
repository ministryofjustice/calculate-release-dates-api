package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.OffenderOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonApiSentenceAndOffences
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAndOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceTerms
import java.math.BigDecimal
import java.time.LocalDate

data class NormalisedSentenceAndOffence(
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
  override val courtDescription: String?,
  override val fineAmount: BigDecimal?,
  override val revocationDates: List<LocalDate>,
) : SentenceAndOffence {
  constructor(source: PrisonApiSentenceAndOffences, offence: OffenderOffence) : this(
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
    offence,
    source.caseReference,
    source.courtDescription,
    source.fineAmount,
    source.revocationDates,
  )
}
