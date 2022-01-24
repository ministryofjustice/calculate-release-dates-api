package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationRule
import java.time.LocalDate

@Schema(description = "Calculation breakdown details for a release date type")
data class ReleaseDateCalculationBreakdown(
  @Schema(description = "Calculation rules used to determine this calculation.", example = "[HDCED_LT_18_MONTHS]")
  val rules: Set<CalculationRule> = emptySet(),
  @Schema(description = "Amount of adjustment in days")
  val adjustedDays: Int = 0,
  @Schema(description = "Final release date (after adjustment has been applied)")
  val releaseDate: LocalDate = LocalDate.now(),
)
