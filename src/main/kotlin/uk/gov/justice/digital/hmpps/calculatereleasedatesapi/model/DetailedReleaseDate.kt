package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import java.time.LocalDate

data class DetailedReleaseDate(
  val releaseDateType: ReleaseDateType,
  val releaseDateTypeFullName: String,
  val date: LocalDate,
  val hints: List<ReleaseDateHint>,
)