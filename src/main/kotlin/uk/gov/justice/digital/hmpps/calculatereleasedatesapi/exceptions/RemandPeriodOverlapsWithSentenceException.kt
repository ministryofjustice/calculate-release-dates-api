package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode

/*
  This exception occurs when a remand period overlaps with a sentence.
 */
class RemandPeriodOverlapsWithSentenceException(message: String) : CrdCalculationValidationException(message, ValidationCode.REMAND_OVERLAPS_WITH_SENTENCE)
