package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import java.time.LocalDateTime

data class LatestCalculation(
  val prisonerId: String,
  val bookingId: Long,
  val calculatedAt: LocalDateTime,
  val establishment: String?,
  val reason: String,
  val source: CalculationSource,
  val dates: List<DetailedDate>,
)
