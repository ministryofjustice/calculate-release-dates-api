package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external

import java.time.LocalDate

data class OffenderKeyDatesResponse (
  val sentenceExpiryDate: LocalDate? = null,
  val conditionalReleaseDate: LocalDate? = null,
  val licenceExpiryDate: LocalDate? = null,
)

