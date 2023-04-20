package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external

data class RegionBankHolidays(
  val division: String,
  val events: List<BankHoliday>,
)
