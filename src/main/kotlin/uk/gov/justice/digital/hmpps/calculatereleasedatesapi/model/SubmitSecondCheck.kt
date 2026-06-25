package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

data class SubmitSecondCheckRequest(
  val prisonerId: String,
  val checkedByUsername: String,
)
