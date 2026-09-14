package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Request for a page")
data class PageRequest(
  @Schema(description = "The number of the page to return with 1 being the first page")
  val pageNumber: Int,
  @Schema(description = "The maximum size of the page")
  val size: Int,
)
