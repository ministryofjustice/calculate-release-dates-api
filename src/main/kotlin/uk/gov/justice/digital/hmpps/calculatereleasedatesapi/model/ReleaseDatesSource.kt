package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SourceType

data class ReleaseDatesSource(
  val type: SourceType,
  val bookingId: Number,
)
