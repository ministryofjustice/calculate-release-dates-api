package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.ReleaseMultiplier
import java.time.LocalDate

data class SentenceLevelDates(
  val sentence: AbstractSentence,
  val groupIndex: Int,
  val impactsFinalReleaseDate: Boolean,
  val releaseMultiplier: ReleaseMultiplier,
  val dates: Map<ReleaseDateType, LocalDate>,
)
