package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.event

data class PrisonerMergedEvent(
  val additionalInformation: PrisonerMergedAdditionalInformation,
)
data class PrisonerMergedAdditionalInformation(
  val removedNomsNumber: String,
  val nomsNumber: String,
)
