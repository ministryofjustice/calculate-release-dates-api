package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

data class GenuineOverrideRequest(
  val dates: List<GenuineOverrideDate>,
  val reason: GenuineOverrideReason,
  val reasonFurtherDetail: String,
)
