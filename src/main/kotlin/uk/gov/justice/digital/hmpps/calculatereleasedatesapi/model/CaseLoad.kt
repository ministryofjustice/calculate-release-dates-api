package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Case Load")
class CaseLoad(
  @Schema(required = true, description = "Case Load ID", example = "MDI")
  val caseLoadId: String,

  @Schema(required = true, description = "Full description of the case load", example = "Moorland Closed (HMP & YOI)")
  val description: String,

  @Schema(
    required = true,
    description = "Type of case load. Note: Reference Code CSLD_TYPE",
    example = "INST",
    allowableValues = ["COMM", "INST", "APP"],
  )
  val type: String,

  @Schema(
    description = "Functional Use of the case load",
    example = "GENERAL",
    allowableValues = ["GENERAL", "ADMIN"],
  )
  val caseloadFunction: String? = null,

  @Schema(
    required = true,
    description = "Indicates that this caseload in the context of a staff member is the current active",
    example = "false",
  )
  val currentlyActive: Boolean,
)
