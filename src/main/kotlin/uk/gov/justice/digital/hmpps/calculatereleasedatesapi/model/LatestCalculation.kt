package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import java.time.LocalDateTime

data class LatestCalculation(
  val prisonerId: String,
  val bookingId: Long,
  val calculatedAt: LocalDateTime,
  val checkedAt: LocalDateTime?,
  val calculationRequestId: Long?,
  val establishment: String?,
  val reason: String,
  val reasonFurtherDetail: String?,
  val source: CalculationSource,
  val dates: List<DetailedDate>,
  val calculatedByUsername: String,
  val checkedByUsername: String?,
  val calculatedByDisplayName: String,
  val checkedByDisplayName: String?,
  val calculationType: String,
)
