package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate
import java.util.UUID

data class RecordARecallRequest(
  val revocationDate: LocalDate,
  @param:Schema(description = "The date the person was returned to custody. Optional if the person was already in prison at the revocation date ")
  val returnToCustodyDate: LocalDate? = null,
  val recallId: UUID? = null,
)
