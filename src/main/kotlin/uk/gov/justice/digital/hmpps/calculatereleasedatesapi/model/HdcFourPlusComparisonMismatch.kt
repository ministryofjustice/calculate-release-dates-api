package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import java.time.LocalDate

data class HdcFourPlusComparisonMismatch(
  val personId: String,
  val lastName: String?,
  val misMatchType: MismatchType,
  val hdcedFourPlusDate: LocalDate,
  val establishment: String?,
  val releaseDate: ReleaseDate? = null,
  val fatalException: String?,
)
