package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationRule
import java.time.LocalDate

@Schema(description = "Calculation breakdown details for a release date type - based on the UI design")
data class ReleaseDateCalculationBreakdown(
  @Schema(description = "Calculation rules used to determine this calculation.", example = "[HDCED_LT_18_MONTHS]")
  val rules: Set<CalculationRule> = emptySet(),
  @Schema(description = "Some calculation rules have rule related adjustments associated ", example = "{ HDCED_LT_18_MONTHS: (5, days) }")
  val rulesWithExtraAdjustments: Map<CalculationRule, AdjustmentDuration> = emptyMap(),
  @Schema(description = "Amount of adjustment in days")
  val adjustedDays: Int = 0,
  @Schema(description = "Final release date (after adjustments have been applied)")
  val releaseDate: LocalDate = LocalDate.now(),
  @Schema(description = "Based on the screen design, the unadjusted date isn't a consistent value but is as per the screen design")
  val unadjustedDate: LocalDate = LocalDate.now(),
)
