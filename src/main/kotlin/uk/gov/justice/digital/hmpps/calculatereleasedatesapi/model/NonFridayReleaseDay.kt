package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import java.time.LocalDate

data class NonFridayReleaseDay(
  val date: LocalDate,
  val usePolicy: Boolean = false,
)
