package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationRule
import java.time.LocalDate

@Schema(description = "Calculation breakdown details for a release date type")
data class ReleaseDateCalculationBreakdown(
  @ArraySchema(
    arraySchema = Schema(
      description = "Calculation rules used to determine this calculation.", example = "[\"HDCED_GE_12W_LT_18M\"]"
    )
  )
  val rules: Set<CalculationRule> = emptySet(),
  @Schema(description = "Adjustments details associated that are specifically added as part of a rule")
  val rulesWithExtraAdjustments: Map<CalculationRule, AdjustmentDuration> = emptyMap(),
  @Schema(description = "Amount of adjustment in days")
  val adjustedDays: Int = 0,
  @Schema(description = "Final release date (after all adjustments have been applied)")
  val releaseDate: LocalDate = LocalDate.now(),
  @Schema(description = "Based on the screen design, the unadjusted date isn't derived in a consistent manner but is set as per the screen design")
  val unadjustedDate: LocalDate = LocalDate.now(),
)
