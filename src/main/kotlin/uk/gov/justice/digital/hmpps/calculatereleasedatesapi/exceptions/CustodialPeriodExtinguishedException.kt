package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode

class CustodialPeriodExtinguishedException(
  message: String,
  validationCode: ValidationCode
) : CrdCalculationValidationException(message, validationCode)
