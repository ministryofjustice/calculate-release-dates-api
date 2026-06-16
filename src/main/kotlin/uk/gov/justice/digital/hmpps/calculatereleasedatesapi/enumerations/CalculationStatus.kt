package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations

enum class CalculationStatus {
  SECOND_CHECK_INITIATED,
  SECOND_CHECK_CONFIRMED,
  PRELIMINARY,
  CONFIRMED,
  ERROR,
  TEST,
  RECORD_A_RECALL,
  BULK,
  OVERRIDDEN,
}
