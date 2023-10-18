package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import io.swagger.v3.oas.annotations.media.Schema

data class ManualComparisonInput(

  @Schema(description = "The prisoner ids the analysis was run against")
  val prisonerIds: List<String>,

)
