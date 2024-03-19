package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import java.time.LocalDateTime

data class LatestCalculation(
  val prisonerId: String,
  val calculatedAt: LocalDateTime?,
  val location: String?,
  val reason: String,
  val source: CalculationSource,
  val dates: Map<ReleaseDateType, DetailedDate>,
)
