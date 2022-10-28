package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation

import io.swagger.v3.oas.annotations.media.Schema

data class ValidationMessages(
  @Schema(description = "Indicates the result of the validation, VALID means there are no errors")
  val type: ValidationType,
  val messages: List<ValidationMessage> = listOf()
) {

  fun toErrorString(): String {
    return "The data for this prisoner is ${if (type == ValidationType.UNSUPPORTED_SENTENCE) "unsupported" else "invalid" }\n" +
      messages.joinToString(separator = "\n") { "${if (it.sentenceSequence != null) "Sentence ${it.sentenceSequence} is invalid: " else ""}${it.message}" }
  }
}
