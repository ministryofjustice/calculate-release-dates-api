package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.remandandsentencing

data class LegacyData(
  val lifeSentence: Boolean,
  val sentenceTermCode: String,
  val sentenceTermDescription: String,
)
