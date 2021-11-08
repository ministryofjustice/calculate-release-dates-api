package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

data class RegionBankHolidays(
  val division: String,
  val events: List<BankHoliday>
)
