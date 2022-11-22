package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external

import java.time.LocalDate

data class FixedTermRecallDetails(
  val bookingId: Long,
  val returnToCustodyDate: LocalDate,
  val recallLength: Int = 0,
)
