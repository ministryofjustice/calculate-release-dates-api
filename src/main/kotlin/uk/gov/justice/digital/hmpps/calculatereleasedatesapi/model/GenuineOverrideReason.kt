package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

enum class GenuineOverrideReason(val description: String, val displayOrder: Int, val requiresFurtherDetail: Boolean) {
  TRIAL_RECORD_OR_BREAKDOWN_DOES_NOT_MATCH_OVERALL_SENTENCE_LENGTH("Trial record sheet/sentence breakdown doesn’t match overall sentence length", 0, false),
  RELEASE_DATE_FROM_ANOTHER_CUSTODY_PERIOD("Release dates (such as SLED or TUSED) from another period of custody are missing", 1, false),
  POWER_TO_DETAIN("An application for a power to detain has been approved", 2, false),
  RELEASE_DATE_ON_WEEKEND_OR_HOLIDAY("The release dates fall on a weekend or public holiday", 3, false),
  CROSS_BORDER_SECTION_RELEASE_DATE("The dates need to be replaced with cross border section release dates", 4, false),
  AGGRAVATING_FACTOR_OFFENCE("One or more offences have been characterised by an aggravating factor (such as terror)", 5, false),
  OTHER("The reason is not on the list", 5, true),
  ;

  fun toResponse() = GenuineOverrideReasonResponse(
    code = this.name,
    description = this.description,
    displayOrder = this.displayOrder,
    requiresFurtherDetail = this.requiresFurtherDetail,
  )
}
