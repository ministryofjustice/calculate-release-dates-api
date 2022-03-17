package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

enum class SentenceType {

  STANDARD_DETERMINATE,
  STANDARD_RECALL,
  FIXED_TERM_RECALL_14,
  FIXED_TERM_RECALL_28;

  val isRecall: Boolean
    get() {
      return listOf(STANDARD_RECALL, FIXED_TERM_RECALL_28, FIXED_TERM_RECALL_14).contains(this)
    }

  val isFixedTermRecall: Boolean
    get() {
      return listOf(FIXED_TERM_RECALL_28, FIXED_TERM_RECALL_14).contains(this)
    }
}
