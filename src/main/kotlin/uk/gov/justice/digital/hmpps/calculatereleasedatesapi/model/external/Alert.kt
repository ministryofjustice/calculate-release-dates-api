package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external

import java.time.LocalDate

data class Alert(
  val dateCreated: LocalDate,
  val dateExpires: LocalDate? = null,
  val alertType: String,
  val alertCode: String,
)
