package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import java.time.LocalDate

data class RecordARecallRequest(
  val revocationDate: LocalDate,
  val arrestDate: LocalDate? = null,
) {

  // UAL required if there is more than one day between revocation and arrest.
  fun requiresUal(): Boolean = arrestDate?.isAfter(revocationDate.plusDays(1)) == true
}
