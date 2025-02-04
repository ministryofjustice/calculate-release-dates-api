package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model
enum class RecallType(
  val lengthInDays: Int? = null,
  val isFixedTermRecall: Boolean = false,
) {
  STANDARD_RECALL(),
  FIXED_TERM_RECALL_14(14, true),
  FIXED_TERM_RECALL_28(28, true),
}
