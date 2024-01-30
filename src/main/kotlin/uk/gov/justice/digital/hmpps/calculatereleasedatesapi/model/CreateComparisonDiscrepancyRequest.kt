package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.DiscrepancyImpact
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.DiscrepancyPriority

data class CreateComparisonDiscrepancyRequest(

  @Schema(description = "The impact of the discrepancy")
  @field:NotNull
  val impact: DiscrepancyImpact,

  @Schema(description = "The causes for the mismatch")
  @field:NotNull
  val causes: List<DiscrepancyCause>,

  @Schema(description = "Any extra detail about the discrepancy")
  @field:NotBlank
  val detail: String,

  @Schema(description = "The priority of resolving the discrepancy")
  @field:NotNull
  val priority: DiscrepancyPriority,

  @Schema(description = "The recommended action that needs to be taken for this discrepancy")
  @field:NotBlank
  val action: String,
)
