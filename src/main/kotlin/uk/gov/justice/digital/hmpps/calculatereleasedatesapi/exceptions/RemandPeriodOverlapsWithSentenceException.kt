package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.REMAND_OVERLAPS_WITH_SENTENCE

/*
  This exception occurs when a remand period overlaps with a sentence.
 */
class RemandPeriodOverlapsWithSentenceException(message: String) : CrdCalculationValidationException(message, REMAND_OVERLAPS_WITH_SENTENCE)
