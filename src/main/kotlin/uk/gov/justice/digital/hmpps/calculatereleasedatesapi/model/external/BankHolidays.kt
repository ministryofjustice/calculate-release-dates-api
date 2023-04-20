package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external

import com.fasterxml.jackson.annotation.JsonProperty

data class BankHolidays(
  @JsonProperty("england-and-wales")
  val englandAndWales: RegionBankHolidays,

  val scotland: RegionBankHolidays,
  @JsonProperty("northern-ireland")
  val northernIreland: RegionBankHolidays,
)
