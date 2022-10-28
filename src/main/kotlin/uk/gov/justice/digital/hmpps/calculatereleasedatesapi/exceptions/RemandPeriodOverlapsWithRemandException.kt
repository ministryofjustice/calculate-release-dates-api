package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.REMAND_OVERLAPS_WITH_REMAND_DETAILED

/*
  This exception occurs when a remand period overlaps with another remand period.
 */
class RemandPeriodOverlapsWithRemandException(message: String, args: List<String>) : CrdCalculationValidationException(message, REMAND_OVERLAPS_WITH_REMAND_DETAILED, args)
