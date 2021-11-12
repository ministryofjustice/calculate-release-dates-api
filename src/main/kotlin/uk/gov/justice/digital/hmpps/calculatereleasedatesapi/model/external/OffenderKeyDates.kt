package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external

import java.time.LocalDate

data class OffenderKeyDates (
  val conditionalReleaseDate: LocalDate? = null,
  val licenceExpiryDate: LocalDate? = null,
  val sentenceExpiryDate: LocalDate? = null,
)