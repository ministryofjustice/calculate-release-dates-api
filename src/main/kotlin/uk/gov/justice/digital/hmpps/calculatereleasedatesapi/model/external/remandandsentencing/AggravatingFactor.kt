package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.remandandsentencing

data class AggravatingFactor(
  val code: String,
  val title: String,
  val description: String,
  val displayOrder: Int,
)
