package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi

import io.swagger.v3.oas.annotations.media.Schema

data class AddressUsageDto(
  @Schema(description = "Address ID of the associated address", example = "23422313")
  private val addressId: Long? = null,

  @Schema(description = "The address usages", example = "HDC")
  private val addressUsage: String? = null,

  @Schema(description = "The address usages description", example = "HDC Address")
  private val addressUsageDescription: String? = null,

  @Schema(description = "Active Flag", example = "true")
  private val activeFlag: Boolean? = null,
)
