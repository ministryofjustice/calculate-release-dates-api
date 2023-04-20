package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external

import java.time.LocalDate

data class SentenceAdjustment(
  val sentenceSequence: Int,
  val active: Boolean,
  val fromDate: LocalDate? = null,
  val toDate: LocalDate? = null,
  val numberOfDays: Int,
  val type: SentenceAdjustmentType,
)

enum class SentenceAdjustmentType {
  RECALL_SENTENCE_REMAND,
  RECALL_SENTENCE_TAGGED_BAIL,
  REMAND,
  TAGGED_BAIL,
  UNUSED_REMAND,
}
