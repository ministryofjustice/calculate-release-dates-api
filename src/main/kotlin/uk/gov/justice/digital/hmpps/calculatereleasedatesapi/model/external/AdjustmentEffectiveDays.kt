package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external

import java.util.UUID

data class AdjustmentEffectiveDays(
  val id: UUID,
  val effectiveDays: Int,
  val person: String,
)
