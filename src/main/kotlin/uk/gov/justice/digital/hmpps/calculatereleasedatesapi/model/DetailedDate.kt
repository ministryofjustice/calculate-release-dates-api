package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import java.time.LocalDate

data class DetailedDate(
  val type: ReleaseDateType,
  val description: String,
  val date: LocalDate,
  var hints: List<ReleaseDateHint>,
)
