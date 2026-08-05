package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.remandandsentencing

import java.time.LocalDate
import java.time.ZonedDateTime

data class Recall(
  val recallUuid: String,
  val prisonerId: String,
  val revocationDate: LocalDate? = null,
  val returnToCustodyDate: LocalDate? = null,
  val inPrisonOnRevocationDate: Boolean? = null,
  val recallType: String,
  val createdAt: ZonedDateTime,
  val createdByUsername: String? = null,
  val createdByPrison: String? = null,
  val source: String,
  val courtCases: List<RecallCourtCaseDetails>,
  val ual: UAL? = null,
  val calculationRequestId: Int? = null,
  val isManual: Boolean,
)
