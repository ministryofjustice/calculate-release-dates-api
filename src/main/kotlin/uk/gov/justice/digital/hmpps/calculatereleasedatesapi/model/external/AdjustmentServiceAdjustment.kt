package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate
import java.util.UUID

@Schema(description = "The adjustment and its identifier")
data class AdjustmentServiceAdjustment(
  val id: UUID?,
  val bookingId: Long,
  val sentenceSequence: Int?,
  val person: String,
  val adjustmentType: AdjustmentServiceAdjustmentType,
  val toDate: LocalDate?,
  val fromDate: LocalDate?,
  val days: Int?,
  val daysBetween: Int?,
  val effectiveDays: Int? = null,
) {}
