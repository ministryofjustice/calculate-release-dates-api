package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi

import java.time.LocalDate

data class PrisonApiCharge(
  val chargeId: Long,
  val offenceCode: String,
  val offenceStatue: String,
  val offenceDescription: String,
  val guilty: Boolean,
  val courtCaseId: Long,
  val bookingId: Long,
  val bookNumber: String,
  val outcomes: List<PrisonApiCourtDateOutcome>,
  val offenceDate: LocalDate? = null,
  val offenceEndDate: LocalDate? = null,
  val courtCaseRef: String? = null,
  val courtLocation: String? = null,
  val sentenceSequence: Int? = null,
  val sentenceDate: LocalDate? = null,
  val resultCode: String?,
  val resultDescription: String?,
  val resultDispositionCode: String?,
  val sentenceType: String? = null,
)
