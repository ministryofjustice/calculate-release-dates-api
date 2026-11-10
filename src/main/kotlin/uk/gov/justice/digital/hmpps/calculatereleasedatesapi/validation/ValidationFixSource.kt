package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Validation fix source")
enum class ValidationFixSource {
  ADJUSTMENTS,
  ADJUDICATIONS,
  FIXED_TERM_RECALLS,
  FIXED_TERM_RECALLS_14,
  FIXED_TERM_RECALLS_28,
}
