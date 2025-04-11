package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

data class AnalyzedBookingAndSentenceAdjustments(
  val bookingAdjustments: List<AnalyzedBookingAdjustment>,
  val sentenceAdjustments: List<AnalyzedSentenceAdjustment>,
)

enum class AnalyzedBookingAndSentenceAdjustmentAnalysisResult {
  NEW,
  SAME,
}
