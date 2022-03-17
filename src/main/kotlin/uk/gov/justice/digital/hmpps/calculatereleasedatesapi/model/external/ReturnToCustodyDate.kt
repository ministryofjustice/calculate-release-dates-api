package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external

import java.time.LocalDate

data class ReturnToCustodyDate(
  val bookingId: Long,
  val returnToCustodyDate: LocalDate
)
