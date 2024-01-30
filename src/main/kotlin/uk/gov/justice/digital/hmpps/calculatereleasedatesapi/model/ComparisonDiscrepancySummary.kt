package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.DiscrepancyImpact
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.DiscrepancyPriority

data class ComparisonDiscrepancySummary(
  @Schema(description = "The impact of the discrepancy")
  val impact: DiscrepancyImpact,

  @Schema(description = "The causes for the mismatch")
  val causes: List<DiscrepancyCause>,

  @Schema(description = "Any extra detail about the discrepancy")
  val detail: String?,

  @Schema(description = "The priority of resolving the discrepancy")
  val priority: DiscrepancyPriority,

  @Schema(description = "The recommended action that needs to be taken for this discrepancy")
  val action: String,
)
