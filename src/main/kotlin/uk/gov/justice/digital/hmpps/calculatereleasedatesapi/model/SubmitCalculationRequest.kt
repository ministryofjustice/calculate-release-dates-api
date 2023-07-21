package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

data class SubmitCalculationRequest(
  val calculationFragments: CalculationFragments,
  val approvedDates: List<ManualEntrySelectedDate>?,
)
