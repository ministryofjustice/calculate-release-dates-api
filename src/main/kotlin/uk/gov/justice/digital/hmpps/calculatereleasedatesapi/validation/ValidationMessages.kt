package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation


data class ValidationMessages(
  val messages: MutableList<ValidationMessage> = mutableListOf(),
  val excludedValidationTypes: List<ValidationType> = listOf()
) {
  fun isNotEmpty() = messages.isNotEmpty()

  fun addAll(messages: List<ValidationMessage>) {
    this.messages += messages.filterNot { excludedValidationTypes.contains(it.type) }
  }
}
