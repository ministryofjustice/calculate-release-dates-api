package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation

data class ValidationMessage(
  val message: String,
  val code: ValidationCode,
  val sentenceSequence: Int? = null,
  val arguments: List<String> = listOf()
)
