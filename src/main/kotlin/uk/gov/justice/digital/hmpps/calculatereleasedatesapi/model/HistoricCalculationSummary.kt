package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationType
import java.time.LocalDateTime

data class HistoricCalculationSummary(
  val calculationDate: LocalDateTime,
  val calculationSource: CalculationSource,
  val calculationType: CalculationType?,
  val crdsCalculationId: Long?,
  val nomisCalculationId: Long?,
  val reasonDescription: String,
  val reasonFurtherDetail: String?,
  val genuineOverrideReasonDescription: String?,
  val calculatedByDisplayName: String,
  val establishmentCalculatedAtDescription: String?,
)
