package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi

import io.swagger.v3.oas.annotations.media.Schema

data class Email(
  @Schema(description = "Email")
  var email: String? = null,
)
