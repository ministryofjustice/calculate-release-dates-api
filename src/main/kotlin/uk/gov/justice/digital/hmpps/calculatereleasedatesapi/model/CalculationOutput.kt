package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

data class CalculationOutput(
  val sentences: List<CalculableSentence>,
  val sentenceGroup: List<SentenceGroup>,
  val calculationResult: CalculationResult,
  val sentenceLevelDates: List<SentenceLevelDates> = emptyList(),
)
