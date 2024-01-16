package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi

import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.Alert
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.isBeforeOrEqualTo
import java.time.LocalDate

data class Person(

  @Schema(description = "Prisoner Identifier", example = "A1234AA", requiredMode = Schema.RequiredMode.REQUIRED)
  var prisonerNumber: String,

  var dateOfBirth: LocalDate,

  var lastName: String,

  val alerts: List<Alert>,
) {
  fun isActiveSexOffender(): Boolean {
    return activeAlerts().any {
      it.alertType == "S" &&
        (
          it.alertCode == "SOR" || // Sex offence register
            it.alertCode == "SR" // On sex offender register
          )
    }
  }

  private fun activeAlerts(): List<Alert> {
    return alerts.filter {
      it.dateCreated.isBeforeOrEqualTo(LocalDate.now()) &&
        (it.dateExpires == null || it.dateExpires.isAfter(LocalDate.now()))
    }
  }
}
