package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.remandandsentencing

data class Sentence(
  val sentenceUuid: String,
  val offenceCode: String,
  val offenceStartDate: String,
  val offenceEndDate: String,
  val sentenceDate: String,
  val lineNumber: String,
  val countNumber: String,
  val periodLengths: List<PeriodLength>,
  val sentenceServeType: String,
  val sentenceTypeDescription: String,
  val consecutiveToSentenceUuid: String,
  val aggravatingFactors: List<AggravatingFactor>,
)
