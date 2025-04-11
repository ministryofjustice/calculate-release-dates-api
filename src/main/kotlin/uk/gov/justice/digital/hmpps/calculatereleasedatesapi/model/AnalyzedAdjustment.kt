package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import com.fasterxml.jackson.annotation.JsonUnwrapped
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.adjustmentsapi.model.AdjustmentDto

class AnalyzedAdjustment(
  val analysisResult: AdjustmentAnalysisResult,
) {
  /* @JsonUnwrapped will inline all the adjustment properties here. Unfortunately its not yet supported within a kotlin data constructor */
  @JsonUnwrapped
  lateinit var adjustment: AdjustmentDto
}

enum class AdjustmentAnalysisResult {
  NEW,
  SAME,
  ;

  fun adjustment(adjustment: AdjustmentDto): AnalyzedAdjustment {
    val analyzedAdjustment = AnalyzedAdjustment(this)
    analyzedAdjustment.adjustment = adjustment
    return analyzedAdjustment
  }
}
