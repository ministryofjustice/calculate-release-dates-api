package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions

import org.springframework.http.HttpStatus
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode

/*
  This exception occurs when a remand period overlaps with another remand period.
 */
class RemandPeriodOverlapsWithRemandException(message: String) : CrdCalculationValidationException(message, ValidationCode.REMAND_OVERLAPS_WITH_REMAND)
