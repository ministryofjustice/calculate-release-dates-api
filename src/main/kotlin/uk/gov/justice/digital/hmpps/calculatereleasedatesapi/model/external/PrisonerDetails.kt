package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi.SentenceCalcDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.isBeforeOrEqualTo
import java.time.LocalDate

data class PrisonerDetails(
  val bookingId: Long,
  val offenderNo: String,
  val firstName: String = "",
  val lastName: String = "",
  val dateOfBirth: LocalDate,
  val alerts: List<Alert> = emptyList(),
  val agencyId: String = "",
  val assignedLivingUnit: AssignedLivingUnit? = null,
  val sentenceDetail: SentenceCalcDates? = null,
) {

  fun activeAlerts(): List<Alert> {
    return alerts.filter {
      it.dateCreated.isBeforeOrEqualTo(LocalDate.now()) &&
        (it.dateExpires == null || it.dateExpires.isAfter(LocalDate.now()))
    }
  }
}
