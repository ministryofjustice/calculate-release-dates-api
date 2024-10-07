package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.manageoffencesapi

data class SchedulePart(
  val id: Int,
  val offences: List<Offence>? = null,
  val partNumber: Int,
)
