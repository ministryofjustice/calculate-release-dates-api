package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity

enum class CalculationType {
  CALCULATED,
  MANUAL_DETERMINATE,
  MANUAL_INDETERMINATE,
  CALCULATED_WITH_APPROVED_DATES,
  MANUAL_OVERRIDE,
  CALCULATED_BY_SPECIALIST_SUPPORT,
}
