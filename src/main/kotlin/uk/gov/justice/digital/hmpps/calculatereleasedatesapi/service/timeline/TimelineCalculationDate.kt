package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline

import java.time.LocalDate

data class TimelineCalculationDate(
  val date: LocalDate,
  val type: TimelineCalculationType,
)
