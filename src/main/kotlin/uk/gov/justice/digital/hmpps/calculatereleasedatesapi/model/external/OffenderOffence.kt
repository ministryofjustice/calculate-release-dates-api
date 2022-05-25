package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external

import java.time.LocalDate

data class OffenderOffence(
  val offenderChargeId: Long,
  val offenceStartDate: LocalDate?,
  val offenceEndDate: LocalDate? = null,
  val offenceCode: String,
  val offenceDescription: String,
  var indicators: List<String> = listOf()
) {
  val isScheduleFifteenMaximumLife: Boolean get() {
    return this.indicators.any { it == SCHEDULE_15_LIFE_INDICATOR }
  }

  companion object {
    const val SCHEDULE_15_LIFE_INDICATOR = "SCH15/CJIB/L"
  }
}
