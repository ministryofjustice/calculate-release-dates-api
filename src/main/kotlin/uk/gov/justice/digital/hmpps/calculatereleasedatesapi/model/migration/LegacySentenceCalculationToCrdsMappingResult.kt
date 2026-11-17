package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.migration

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "The result of mapping a legacy NOMIS calculation into CRDS")
data class LegacySentenceCalculationToCrdsMappingResult(
  @Schema(description = "The offender sentence calculation id from NOMIS")
  val nomisCalculationId: Long,
  @Schema(description = "The id of any existing calculation request that we found in CRDS. If the source of the original calculation was NOMIS or we simply couldn't match to a calculation in CRDS this will be null.", nullable = true)
  val crdsCalculationId: Long?,
)
