package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations

enum class ComparisonType {
  ESTABLISHMENT_FULL,
  ESTABLISHMENT_HDCED4PLUS,
  MANUAL,
}

public fun manualComparisonTypes() = setOf(ComparisonType.MANUAL)

public fun nonManualComparisonTypes() = setOf(ComparisonType.ESTABLISHMENT_FULL, ComparisonType.ESTABLISHMENT_HDCED4PLUS)
