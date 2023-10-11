package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculatedReleaseDates

data class ValidationResult(
  val messages: List<ValidationMessage>,
  val booking: Booking?,
  val calculatedReleaseDates: CalculatedReleaseDates?,
)
