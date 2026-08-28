package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi

import com.fasterxml.jackson.annotation.JsonInclude
import java.time.LocalDate

@JsonInclude(JsonInclude.Include.NON_NULL)
data class PrisonApiCourtDateOutcome(
  val id: Long,
  val date: LocalDate,
  val resultCode: String?,
  val resultDescription: String?,
  val resultDispositionCode: String?,
)
