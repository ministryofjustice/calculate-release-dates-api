package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

data class AnalysedBookingAndSentenceAdjustments(
  val bookingAdjustments: List<AnalysedBookingAdjustment>,
  val sentenceAdjustments: List<AnalysedSentenceAdjustment>,
)

enum class AnalysedBookingAndSentenceAdjustmentAnalysisResult {
  NEW,
  SAME,
}
