package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceType
import java.time.LocalDate

data class ConcurrentSentenceBreakdown(
  override val sentencedAt: LocalDate,
  override val sentenceLength: String,
  override val sentenceLengthDays: Int,
  override val dates: Map<SentenceType, DateBreakdown>,
  val sequence: String
) : SentenceBreakdown
