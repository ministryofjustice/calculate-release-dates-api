package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import java.time.LocalDate

data class PrisonerDetails(
  val bookingId: Long,
  val offenderNo: String,
  val firstName: String = "",
  val lastName: String = "",
  val dateOfBirth: LocalDate,
)
