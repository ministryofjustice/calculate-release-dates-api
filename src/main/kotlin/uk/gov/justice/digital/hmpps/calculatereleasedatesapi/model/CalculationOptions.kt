package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

data class CalculationOptions(
  val calculateErsed: Boolean,
  val allowSDSEarlyRelease: Boolean,
)
