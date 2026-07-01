package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "The type content for the validation message", enumAsRef = true)
enum class ValidationMessageContentType {
  // Line breaks (\n) will be converted to <br/> for plain text
  PLAIN_TEXT,
  HTML,
}
