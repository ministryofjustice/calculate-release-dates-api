package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations

enum class AdjustmentType(
  val text: String,
) {
  REMAND("Remand"),
  TAGGED_BAIL("Tagged bail"),
  UNLAWFULLY_AT_LARGE("Unlawfully at large"),
  ADDITIONAL_DAYS_AWARDED("Additional days awarded"),
  RESTORATION_OF_ADDITIONAL_DAYS_AWARDED("Restoration of additional days awarded"),
  RECALL_REMAND("Recall remand"),
  RECALL_TAGGED_BAIL("Recall tagged bail"),
}
