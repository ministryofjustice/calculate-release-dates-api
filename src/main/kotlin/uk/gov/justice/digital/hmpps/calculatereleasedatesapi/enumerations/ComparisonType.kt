package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations

enum class ComparisonType {
  ESTABLISHMENT_FULL,
  MANUAL,
}

fun manualComparisonTypes() = setOf(ComparisonType.MANUAL)

fun nonManualComparisonTypes() = setOf(ComparisonType.ESTABLISHMENT_FULL)
