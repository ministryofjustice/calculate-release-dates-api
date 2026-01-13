package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Validation message details")
data class ValidationMessage(
  val code: ValidationCode,
  val arguments: List<String> = listOf(),
  val message: String = String.format(code.message, *arguments.toTypedArray()),
  val type: ValidationType = code.validationType,
  val calculationUnsupported: Boolean = code.validationType.isUnsupported(),
)
