package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import java.time.LocalDate

data class OffenderOffence(
  val offenderChargeId: Long,
  val offenceDate: LocalDate,
  val offenceCode: String,
  val offenceDescription: String,
)
