package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType

data class ManuallyEnteredDate(
  val dateType: ReleaseDateType,
  val date: SubmittedDate?,
)
