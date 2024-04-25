package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external

import java.math.BigDecimal
import java.time.LocalDate

data class PrisonApiSentenceAndOffences(
  override val bookingId: Long,
  override val sentenceSequence: Int,
  override val lineSequence: Int,
  override val caseSequence: Int,
  override val consecutiveToSequence: Int? = null,
  override val sentenceStatus: String,
  override val sentenceCategory: String,
  override val sentenceCalculationType: String,
  override val sentenceTypeDescription: String,
  override val sentenceDate: LocalDate,
  override val terms: List<SentenceTerms> = emptyList(),
  override val offences: List<OffenderOffence> = emptyList(),
  override val caseReference: String? = null,
  override val courtDescription: String? = null,
  override val fineAmount: BigDecimal? = null,
) : SentenceAndOffences
