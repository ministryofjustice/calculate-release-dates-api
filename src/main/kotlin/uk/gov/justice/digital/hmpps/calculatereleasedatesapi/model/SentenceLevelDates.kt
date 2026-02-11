package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import java.time.LocalDate

data class SentenceLevelDates(
  val sentence: AbstractSentence,
  val groupIndex: Int,
  val impactsFinalReleaseDate: Boolean,
  val releaseMultiplier: Double,
  val dates: Map<ReleaseDateType, LocalDate>,
)
