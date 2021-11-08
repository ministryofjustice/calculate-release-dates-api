package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

data class CalculationBreakdown(
  val concurrentSentences: List<ConcurrentSentenceBreakdown>,
  val consecutiveSentence: ConsecutiveSentenceBreakdown?
)
