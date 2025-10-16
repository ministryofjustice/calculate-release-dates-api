package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

data class PreviousGenuineOverride(
  val calculationRequestId: Long,
  val dates: List<GenuineOverrideDate>,
  val reason: GenuineOverrideReason,
  val reasonFurtherDetail: String?,
)
