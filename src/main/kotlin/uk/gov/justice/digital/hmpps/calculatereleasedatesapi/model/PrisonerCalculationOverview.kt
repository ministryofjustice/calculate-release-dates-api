package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

data class PrisonerCalculationOverview(
  val latestCalculation: LatestCalculation?,
  val recentCalculations: List<HistoricCalculationSummary>,
  val totalCalculationCount: Int,
  val numberOfSentences: Int,
  val hasIndeterminateSentences: Boolean,
)
