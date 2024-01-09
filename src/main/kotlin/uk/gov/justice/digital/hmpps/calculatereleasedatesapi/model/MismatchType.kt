package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

enum class MismatchType {
  NONE,
  RELEASE_DATES_MISMATCH,
  VALIDATION_ERROR,
  UNSUPPORTED_SENTENCE_TYPE,
  UNSUPPORTED_SENTENCE_TYPE_FOR_HDC4_PLUS,
  VALIDATION_ERROR_HDC4_PLUS,
}
