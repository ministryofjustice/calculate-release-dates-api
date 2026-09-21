package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

data class ManualCalculationInputResponse(
  val manuallyEnteredDates: List<DetailedDate>,
  val mode: ManualCalculationEntryMode,
)
