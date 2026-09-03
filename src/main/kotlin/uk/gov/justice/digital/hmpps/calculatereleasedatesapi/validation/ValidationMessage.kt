package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation

import io.swagger.v3.oas.annotations.media.Schema
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@Schema(description = "Validation message details")
data class ValidationMessage(
  val code: ValidationCode,
  val arguments: List<String> = listOf(),
  @Deprecated(
    "This field refers to NOMIS warnings and errors. Use dpsMessage for DPS style messages instead.",
    level = DeprecationLevel.WARNING,
  )
  val message: String = safeFormat(code.nomisMessage, arguments),
  val dpsMessage: String = safeFormat(code.dpsMessage, arguments),
  val type: ValidationType = code.validationType,
  val calculationUnsupported: Boolean = code.validationType.isUnsupported(),
  val contentType: ValidationMessageContentType = code.contentType,
) {
  companion object {
    /*
     * fall back to the un-formatted template instead of throwing an exception
    */
    private fun safeFormat(template: String, arguments: List<String>): String = try {
      String.format(template, *arguments.toTypedArray())
    } catch (e: java.util.MissingFormatArgumentException) {
      log.debug(e.message)
      template
    }

    private val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}
