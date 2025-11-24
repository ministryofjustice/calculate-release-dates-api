package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationOrder.INVALID
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationOrder.UNSUPPORTED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationOrder.WARNING

enum class ValidationType(
  val order: ValidationOrder,
) {

  UNSUPPORTED_SENTENCE(UNSUPPORTED),
  UNSUPPORTED_CALCULATION(UNSUPPORTED),
  VALIDATION(INVALID),
  INCORRECT_OFFENCE(INVALID),
  SUSPENDED_OFFENCE(INVALID),
  MANUAL_ENTRY_JOURNEY_REQUIRED(UNSUPPORTED),
  CONCURRENT_CONSECUTIVE(WARNING),
  ;

  fun isUnsupported(): Boolean = order == UNSUPPORTED
  fun excludedInSave(): Boolean = order == WARNING
}
