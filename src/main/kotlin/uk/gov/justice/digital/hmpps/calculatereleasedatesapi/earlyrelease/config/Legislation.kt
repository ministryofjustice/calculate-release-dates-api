package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config

import java.time.LocalDate

interface Legislation {
  val legislationName: LegislationName
  fun commencementDate(): LocalDate
}
