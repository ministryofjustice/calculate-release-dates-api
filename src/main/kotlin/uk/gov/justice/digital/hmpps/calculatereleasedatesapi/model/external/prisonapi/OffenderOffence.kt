package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate

data class OffenderOffence(
  @Schema(description = "Internal ID for charge relating to offender")
  var offenderChargeId: Long? = null,

  @Schema(description = "Offence Start Date")
  val offenceStartDate: LocalDate? = null,

  @Schema(description = "Offence End Date")
  val offenceEndDate: LocalDate? = null,

  @Schema(description = "Offence Code")
  val offenceCode: String? = null,

  @Schema(description = "Offence Description")
  val offenceDescription: String? = null,

  @Schema(description = "Offence Indicators")
  val indicators: List<String>? = null,
)
