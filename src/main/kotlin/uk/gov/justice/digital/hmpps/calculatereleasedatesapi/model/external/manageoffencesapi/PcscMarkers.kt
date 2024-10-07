package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.manageoffencesapi

data class PcscMarkers(
  val inListA: Boolean,
  val inListB: Boolean,
  val inListC: Boolean,
  val inListD: Boolean,
)

data class OffencePcscMarkers(
  val offenceCode: String,
  val pcscMarkers: PcscMarkers,
)
