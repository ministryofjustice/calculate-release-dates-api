package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import java.time.LocalDate

data class GenuineOverrideDate(
  val dateType: ReleaseDateType,
  val date: LocalDate,
)
