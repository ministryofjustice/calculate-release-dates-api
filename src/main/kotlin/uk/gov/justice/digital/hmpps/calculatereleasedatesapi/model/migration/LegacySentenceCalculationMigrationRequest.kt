package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.migration

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "A request to migrate all sentence calculations for a prisoner")
data class LegacySentenceCalculationMigrationRequest(
  @Schema(description = "All sentence calculations for a prisoner")
  val sentenceCalculations: List<LegacySentenceCalculationDto>,
)
