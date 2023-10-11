package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Sentence Summary")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class SentenceSummary(
  @Schema(description = "Prisoner Identifier", example = "A1234AA", requiredMode = Schema.RequiredMode.REQUIRED)
  val prisonerNumber: String? = null,

  @Schema(description = "Most recent term in prison")
  val latestPrisonTerm: PrisonTerm? = null,

)
