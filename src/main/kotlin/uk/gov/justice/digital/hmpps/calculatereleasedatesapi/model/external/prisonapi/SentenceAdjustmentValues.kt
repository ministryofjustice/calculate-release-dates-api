package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi

import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAdjustmentType
import java.time.LocalDate

@Schema(description = "Sentence Adjustment values")
class SentenceAdjustmentValues(
  @Schema(description = "Sentence sequence", example = "1")
  var sentenceSequence: Int? = null,

  @Schema(description = "Adjustment type")
  var type: SentenceAdjustmentType? = null,

  @Schema(description = "Number of days to adjust", example = "12")
  var numberOfDays: Int? = null,

  @Schema(description = "The 'from date' of the adjustment", example = "2022-01-01")
  var fromDate: LocalDate? = null,

  @Schema(description = "The 'to date' of the adjustment", example = "2022-01-31")
  var toDate: LocalDate? = null,

  @Schema(description = "Boolean flag showing if the adjustment is active", example = "true")
  var active: Boolean = false,
)
