package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import java.time.LocalDate

data class BookingCalculation(
  val dates: MutableMap<SentenceType, LocalDate> = mutableMapOf()
)
