package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.DiscrepancyCategory
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.DiscrepancySubCategory

data class DiscrepancyCause(

  @Schema(description = "A mismatch cause category")
  val category: DiscrepancyCategory,

  @Schema(description = "A subcategory for a cause of the mismatch")
  val subCategory: DiscrepancySubCategory,

  @Schema(description = "Any other information on the mismatch cause")
  val other: String? = null,
)
