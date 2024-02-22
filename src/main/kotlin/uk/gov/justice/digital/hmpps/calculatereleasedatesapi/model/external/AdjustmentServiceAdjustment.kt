package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external

import java.time.LocalDate
import java.util.UUID

data class AdjustmentServiceAdjustment(
  val id: UUID?,
  val bookingId: Long,
  val sentenceSequence: Int?,
  val person: String,
  val adjustmentType: AdjustmentServiceAdjustmentType,
  val toDate: LocalDate?,
  val fromDate: LocalDate?,
  val days: Int,
  val effectiveDays: Int,
)
