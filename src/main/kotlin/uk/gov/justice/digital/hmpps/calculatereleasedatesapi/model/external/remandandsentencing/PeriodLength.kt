package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.remandandsentencing

data class PeriodLength(
  val years: Int? = null,
  val months: Int? = null,
  val weeks: Int? = null,
  val days: Int? = null,
  val periodOrder: String,
  val periodLengthType: String,
  val legacyData: LegacyData,
  val periodLengthUuid: String,
)
