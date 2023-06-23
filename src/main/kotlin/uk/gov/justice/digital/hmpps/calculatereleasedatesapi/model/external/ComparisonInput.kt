package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external

import com.fasterxml.jackson.databind.JsonNode
import io.swagger.v3.oas.annotations.media.Schema

data class ComparisonInput(
  @Schema(description = "Criteria")
  val criteria: JsonNode?,

  @Schema(description = "Was it manually input")
  val manualInput: Boolean,

  @Schema(description = "The prison the analysis was run against")
  val prison: String?,

)
