package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode

class AdjustmentIsAfterReleaseDateException(message: String, arguments: List<String>) : CrdCalculationValidationException(message, ValidationCode.ADJUSTMENT_AFTER_RELEASE, arguments)
