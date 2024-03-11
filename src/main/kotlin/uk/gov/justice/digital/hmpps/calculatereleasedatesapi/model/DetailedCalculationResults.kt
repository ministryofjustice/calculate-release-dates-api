package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType

data class DetailedCalculationResults(
  val calculationRequestId: Long,
  val dates: Map<ReleaseDateType, DetailedReleaseDate?>,
)
