package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.validator

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationMessage

interface PreCalculationBookingValidator : Validator {
  fun validate(booking: Booking): List<ValidationMessage>
}
