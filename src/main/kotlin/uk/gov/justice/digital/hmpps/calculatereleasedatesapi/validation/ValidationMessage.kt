package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation

import com.fasterxml.jackson.annotation.JsonIgnore
import io.swagger.v3.oas.annotations.media.Schema
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Validation Messages
 *
 * @param code ValidationCode
 * @param arguments List
 * @param message Deprecated, this field refers to NOMIS warnings and errors. Use dpsMessage for DPS style messages instead.
 * @param dpsMessage this field refers to DPS warnings and errors
 * @param type ValidationType
 * @param calculationUnsupported Boolean
 * @param contentType ValidationMessageContentType
 * @param sentenceIdentifier Explicitly identifies the sentence this message relates to
 */
@Schema(description = "Validation message details")
data class ValidationMessage(
  val code: ValidationCode,
  val arguments: List<String> = listOf(),
  val message: String = safeFormat(code.nomisMessage, arguments),
  val dpsMessage: String = safeFormat(code.dpsMessage, arguments),
  val type: ValidationType = code.validationType,
  val calculationUnsupported: Boolean = code.validationType.isUnsupported(),
  val contentType: ValidationMessageContentType = code.contentType,
  @get:JsonIgnore
  val sentenceIdentifier: SentenceIdentifier? = null,
) {
  companion object {
    /*
     * fall back to the un-formatted template instead of throwing an exception
     */
    internal fun safeFormat(template: String, arguments: List<String>): String = try {
      String.format(template, *arguments.toTypedArray())
    } catch (e: java.util.MissingFormatArgumentException) {
      log.debug(e.message)
      template
    }

    private val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}

data class SentenceIdentifier(
  val bookingId: Long,
  val sentenceSequence: Int,
)
