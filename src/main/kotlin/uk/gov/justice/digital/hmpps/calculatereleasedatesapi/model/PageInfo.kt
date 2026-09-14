package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Metadata to support pagination")
data class PageInfo(
  @Schema(description = "The current page number")
  val pageNumber: Int,
  @Schema(description = "The total number of pages available")
  val totalPages: Int,
  @Schema(description = "The total number of items across all pages")
  val totalItems: Int,
)
