package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

enum class MismatchType {
  NONE,
  RELEASE_DATES_MISMATCH,
  VALIDATION_ERROR,
  UNSUPPORTED_SENTENCE_TYPE,
  VALIDATION_ERROR_HDC4_PLUS,
}
