package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "A page of historic calculation summaries along with metadata to support pagination")
data class HistoricCalculationSummaryPage(
  @Schema(description = "The items in this page")
  val items: List<HistoricCalculationSummary>,
  @Schema(description = "Metadata about this page")
  val page: PageInfo,
)
