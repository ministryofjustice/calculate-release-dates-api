package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

enum class GenuineOverrideReason(val description: String, val displayOrder: Int, val requiresFurtherDetail: Boolean) {
  ORDER_OF_IMPRISONMENT_OR_WARRANT_DOES_NOT_MATCH_TRIAL_RECORD("Order of imprisonment/warrant doesn't match trial record sheet", 0, false),
  TERRORISM("Terrorism or terror-related offences", 1, false),
  POWER_TO_DETAIN("Power to detain", 2, false),
  CROSS_BORDER_SECTION_RELEASE_DATE("Cross border section release dates", 3, false),
  ADD_RELEASE_DATE_FROM_ANOTHER_BOOKING("Adding a release date from another booking", 4, false),
  ERS_BREACH("ERS (Early release scheme) breach", 5, false),
  COURT_OF_APPEAL("Court of appeal", 6, false),
  OTHER("The reason is not on this list", 7, true),
  ;

  fun toResponse() = GenuineOverrideReasonResponse(
    code = this.name,
    description = this.description,
    displayOrder = this.displayOrder,
    requiresFurtherDetail = this.requiresFurtherDetail,
  )
}
