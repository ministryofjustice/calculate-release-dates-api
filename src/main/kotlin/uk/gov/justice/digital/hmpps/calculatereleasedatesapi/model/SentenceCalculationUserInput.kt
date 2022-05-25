package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

data class SentenceCalculationUserInput(
  val sentenceSequence: Int,
  val offenceCode: String,
  var isScheduleFifteenMaximumLife: Boolean
)
