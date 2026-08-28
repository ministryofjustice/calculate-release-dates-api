package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.remandandsentencing

data class PrisonerRecallsResponse(
  val recalls: List<Recall>,
  val prisonerRecallTotal: Long,
)
