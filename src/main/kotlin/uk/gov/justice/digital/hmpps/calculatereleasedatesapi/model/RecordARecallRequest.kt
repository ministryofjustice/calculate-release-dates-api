package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import java.time.LocalDate
import java.util.UUID

data class RecordARecallRequest(
  val revocationDate: LocalDate,
  val recallId: UUID? = null,
)
