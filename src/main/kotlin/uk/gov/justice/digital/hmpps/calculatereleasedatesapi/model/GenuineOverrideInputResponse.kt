package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

data class GenuineOverrideInputResponse(
  val mode: GenuineOverrideMode,
  val calculatedDates: List<GenuineOverrideDate>,
  val previousOverrideForExpressGenuineOverride: PreviousGenuineOverride?,
)
