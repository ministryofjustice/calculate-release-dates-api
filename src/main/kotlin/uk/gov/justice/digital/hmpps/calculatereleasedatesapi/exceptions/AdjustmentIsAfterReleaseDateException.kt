package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode

class AdjustmentIsAfterReleaseDateException(message: String, validationCode: ValidationCode) : CrdCalculationValidationException(message, validationCode)
