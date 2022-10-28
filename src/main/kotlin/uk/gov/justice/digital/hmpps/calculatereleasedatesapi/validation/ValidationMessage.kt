package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation

data class ValidationMessage(
  val code: ValidationCode,
  val sentenceSequence: Int? = null,
  val arguments: List<String> = listOf()
) {
  val message
    get() = String.format(code.message, *arguments.toTypedArray())
}
