package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate

data class OffenderOffence(
  @Schema(description = "Internal ID for charge relating to offender")
  private var offenderChargeId: Long? = null,
  @Schema(description = "Offence Start Date")
  private val offenceStartDate: LocalDate? = null,

  @Schema(description = "Offence End Date")
  private val offenceEndDate: LocalDate? = null,

  @Schema(description = "Offence Code")
  private val offenceCode: String? = null,

  @Schema(description = "Offence Description")
  private val offenceDescription: String? = null,

  @Schema(description = "Offence Indicators")
  private val indicators: List<String>? = null,
)
