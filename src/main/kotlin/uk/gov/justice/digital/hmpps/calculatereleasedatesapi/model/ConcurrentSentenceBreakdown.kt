package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import java.time.LocalDate

data class ConcurrentSentenceBreakdown(
  override val sentencedAt: LocalDate,
  override val sentenceLength: String,
  override val sentenceLengthDays: Int,
  override val dates: Map<ReleaseDateType, DateBreakdown>,
  val sentenceCalculationType: String,
  val isSDSPlus: Boolean = false,
  val lineSequence: Int,
  val caseSequence: Int,
  val caseReference: String? = null,
) : SentenceBreakdown
