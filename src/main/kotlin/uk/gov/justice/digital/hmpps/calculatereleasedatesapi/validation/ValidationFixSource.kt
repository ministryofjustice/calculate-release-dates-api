package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Validation fix source")
enum class ValidationFixSource {
  DEFAULT,
  ADJUSTMENTS,
  ADJUDICATIONS,
  COURT_CASES,
}
