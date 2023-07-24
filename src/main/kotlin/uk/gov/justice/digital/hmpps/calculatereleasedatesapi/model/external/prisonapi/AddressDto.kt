package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate

data class AddressDto(

  @Schema(description = "Address Id", example = "543524")
  private val addressId: Long? = null,

  @Schema(description = "Address Type. Note: Reference domain is ADDR_TYPE", example = "BUS")
  private val addressType: String? = null,

  @Schema(description = "Flat", example = "3B")
  private val flat: String? = null,

  @Schema(description = "Premise", example = "Liverpool Prison")
  private val premise: String? = null,

  @Schema(description = "Street", example = "Slinn Street")
  private val street: String? = null,

  @Schema(description = "Locality", example = "Brincliffe")
  private val locality: String? = null,

  @Schema(description = "Town/City. Note: Reference domain is CITY", example = "Liverpool")
  private val town: String? = null,

  @Schema(description = "Postal Code", example = "LI1 5TH")
  private val postalCode: String? = null,

  @Schema(description = "County. Note: Reference domain is COUNTY", example = "HEREFORD")
  private val county: String? = null,

  @Schema(description = "Country. Note: Reference domain is COUNTRY", example = "ENG")
  private val country: String? = null,

  @Schema(description = "Comment", example = "This is a comment text")
  private val comment: String? = null,

  @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Primary Address", example = "Y")
  private val primary: Boolean? = null,

  @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "No Fixed Address", example = "N")
  private val noFixedAddress: Boolean? = null,

  @Schema(description = "Date Added", example = "2005-05-12")
  private val startDate: LocalDate? = null,

  @Schema(description = "Date ended", example = "2021-02-12")
  private val endDate: LocalDate? = null,

  @Schema(description = "The phone number associated with the address")
  private val phones: List<Telephone>? = null,

  @Schema(description = "The address usages/types")
  private val addressUsages: List<AddressUsageDto>? = null,
)
