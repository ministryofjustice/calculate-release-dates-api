package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import jakarta.validation.constraints.NotNull

class OffenderBasicInfo(
  @NotNull
  val personId: String,
  @NotNull
  val latestBookingId: Int,

)
