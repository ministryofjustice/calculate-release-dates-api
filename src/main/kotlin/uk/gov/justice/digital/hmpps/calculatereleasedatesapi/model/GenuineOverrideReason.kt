package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

enum class GenuineOverrideReason(val description: String, val displayOrder: Int, val requiresFurtherDetail: Boolean) {
  RECORD_CROSS_BORDER_SECTION_RELEASE_DATES("To record cross-border section release dates", 0, false),
  RECORD_POWER_TO_DETAIN("To record ‘Power to detain’", 1, false),
  RECORD_ERS_BREACH("To record an ERS (Early release scheme) breach", 2, false),
  ADD_RELEASE_DATES_FROM_PREVIOUS_BOOKING("To add release dates from a previous booking", 3, false),
  MISALIGNED_COURT_DOCUMENTS("There are misaligned court documents", 4, false),
  UNSUPPORTED_SENTENCES("The calculation includes unsupported sentences", 5, false),
  OTHER("Other", 6, true),
  ;

  fun toResponse() = GenuineOverrideReasonResponse(
    code = this.name,
    description = this.description,
    displayOrder = this.displayOrder,
    requiresFurtherDetail = this.requiresFurtherDetail,
  )
}
