package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation

data class ValidationMessages(
  val type: ValidationType,
  val messages: List<ValidationMessage> = listOf()
) {

  fun toErrorString(): String {
    return "The data for this prisoner is ${if (type == ValidationType.UNSUPPORTED_SENTENCE) "unsupported" else "invalid" }\n" +
      messages.joinToString(separator = "\n") { "${if (it.sentenceSequence != null) "Sentence ${it.sentenceSequence} is invalid: " else ""}${it.message}" }
  }
}
