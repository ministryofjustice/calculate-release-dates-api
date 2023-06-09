package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external

import java.util.UUID

data class UpdateOffenderDates(
  val calculationUuid: UUID,
  val submissionUser: String,
  val keyDates: OffenderKeyDates,
  val noDates: Boolean,
  val comment: String? = null,
)
