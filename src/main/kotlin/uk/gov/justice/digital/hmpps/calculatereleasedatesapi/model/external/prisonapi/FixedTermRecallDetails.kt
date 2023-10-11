package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate

class FixedTermRecallDetails(

  @Schema(description = "The booking id")
  var bookingId: Long? = null,

  @Schema(description = "The date the offender returned to custody")
  var returnToCustodyDate: LocalDate? = null,

  @Schema(description = "The length of the fixed term recall")
  var recallLength: Int? = null,
)
