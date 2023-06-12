package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType

data class ManualEntrySelectedDate(
  val dateType: ReleaseDateType,
  val dateText: String,
  val date: SubmittedDate?,
)
