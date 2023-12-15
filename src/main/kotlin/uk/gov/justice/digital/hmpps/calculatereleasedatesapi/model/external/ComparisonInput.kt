package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external

import com.fasterxml.jackson.databind.JsonNode
import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ComparisonType

data class ComparisonInput(
  @Schema(description = "Criteria used in the comparison")
  val criteria: JsonNode?,

  @Schema(description = "The prison the analysis was run against")
  val prison: String?,

  @Schema(description = "The type of comparison that was run")
  val comparisonType: ComparisonType = ComparisonType.ESTABLISHMENT_FULL,
)
