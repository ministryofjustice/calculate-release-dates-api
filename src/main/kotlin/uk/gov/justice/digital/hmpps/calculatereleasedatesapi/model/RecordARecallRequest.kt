package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import java.time.LocalDate

data class RecordARecallRequest(
  val revocationDate: LocalDate,
)
