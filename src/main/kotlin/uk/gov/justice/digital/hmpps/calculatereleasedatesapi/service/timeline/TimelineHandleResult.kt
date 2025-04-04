package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline

data class TimelineHandleResult(
  /* Does this timeline handler require a new calculation of the latest release date. */
  val requiresCalculation: Boolean = true,
  /* Does this timeline handler require that the latest release date is not overwritten (external movements releases) */
  val skipCalculationForEntireDate: Boolean = false,
)
