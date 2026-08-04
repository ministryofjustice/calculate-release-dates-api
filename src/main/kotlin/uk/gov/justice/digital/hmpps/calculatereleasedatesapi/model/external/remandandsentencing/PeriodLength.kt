package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.remandandsentencing

data class PeriodLength(
  val years: Int,
  val months: Int,
  val weeks: Int,
  val days: Int,
  val periodOrder: String,
  val periodLengthType: String,
  val legacyData: LegacyData,
  val periodLengthUuid: String,
)
