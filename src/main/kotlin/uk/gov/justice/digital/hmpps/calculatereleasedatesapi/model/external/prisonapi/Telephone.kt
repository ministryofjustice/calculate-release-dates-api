package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank

data class Telephone(

  @Schema(description = "Phone Id", example = "2234232")
  val phoneId: Long? = null,

  @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Telephone number", example = "0114 2345678")
  val number: @NotBlank String? = null,

  @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Telephone type", example = "TEL")
  val type: @NotBlank String? = null,

  @Schema(description = "Telephone extension number", example = "123")
  val ext: String? = null,

)
