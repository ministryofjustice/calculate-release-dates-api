package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.remandandsentencing

import java.time.LocalDateTime

data class Recall(
  val recallUuid: String,
  val prisonerId: String,
  val revocationDate: String,
  val returnToCustodyDate: String,
  val inPrisonOnRevocationDate: Boolean,
  val recallType: String,
  val createdAt: LocalDateTime,
  val createdByUsername: String,
  val createdByPrison: String,
  val source: String,
  val courtCases: List<CourtCase>,
  val ual: UAL,
  val calculationRequestId: Int,
  val isManual: Boolean,
)
