package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.migration

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "The result of migrating a prisoner")
data class LegacySentenceCalculationMigrationResponse(
  @Schema(description = "All successfully migrated calculations including whether we matched them to a CRDS calculation or not")
  val mappingResults: List<LegacySentenceCalculationToCrdsMappingResult>,
)
