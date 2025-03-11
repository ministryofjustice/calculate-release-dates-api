package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external

import com.fasterxml.jackson.annotation.JsonIgnore
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

  @JsonIgnore
  fun isActiveSexOffender(): Boolean =
    activeAlerts().any {
      it.alertType == "S" &&
        (
          it.alertCode == "SOR" || // Sex offence register
            it.alertCode == "SR"
          ) // On sex offender register
    }
}
