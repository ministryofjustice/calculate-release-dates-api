package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate

data class Person(

  @Schema(description = "Prisoner Identifier", example = "A1234AA", requiredMode = Schema.RequiredMode.REQUIRED)
  var prisonerNumber: String,

  var dateOfBirth: LocalDate,

)