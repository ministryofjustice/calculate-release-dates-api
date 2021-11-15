package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import java.time.LocalDate

data class BookingCalculation(
  val dates: MutableMap<ReleaseDateType, LocalDate> = mutableMapOf(),
  var calculationRequestId: Long = -1
)
