package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

data class ReleaseDates(
  val prisonerNumber: String,
  val source: ReleaseDatesSource,
  val dates: List<ReleaseDate>,
)
