package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.validator

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationOutput
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationMessage

interface PostCalculationValidator : Validator {
  fun validate(calculationOutput: CalculationOutput, booking: Booking): List<ValidationMessage>
}
