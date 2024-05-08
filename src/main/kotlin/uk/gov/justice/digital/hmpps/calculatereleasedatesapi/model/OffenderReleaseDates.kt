package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import java.time.LocalDateTime

data class OffenderReleaseDates(
  val bookingId: Long,
  val calculationRequestId: Long,
  val calculatedAt: LocalDateTime,
  val reason: String,
  val source: CalculationSource,
  val dates: List<DetailedDate>,
)
