package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.manageoffencesapi

data class Schedule(
  val act: String,
  val code: String,
  val id: Int,
  val scheduleParts: List<SchedulePart>,
  val url: String,
)
