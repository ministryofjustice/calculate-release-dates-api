package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation

enum class ValidationType {
  UNSUPPORTED_SENTENCE,
  UNSUPPORTED_CALCULATION,
  VALIDATION,
  UNSUPPORTED_OFFENCE,
  SUSPENDED_OFFENCE,
  MANUAL_ENTRY_JOURNEY_REQUIRED,
  CONCURRENT_CONSECUTIVE,
  ;

  fun isUnsupported(): Boolean = listOf(UNSUPPORTED_OFFENCE, UNSUPPORTED_SENTENCE, UNSUPPORTED_CALCULATION, MANUAL_ENTRY_JOURNEY_REQUIRED).contains(this)

  fun excludedInSave(): Boolean = this == CONCURRENT_CONSECUTIVE


}
