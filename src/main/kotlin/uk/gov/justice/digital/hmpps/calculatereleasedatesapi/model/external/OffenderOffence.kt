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
  val isPcscSds: Boolean get() {
    return this.indicators.any { it == PCSC_SDS }
  }
  val isPcscSec250: Boolean get() {
    return this.indicators.any { it == PCSC_SEC250 }
  }
  val isPcscSdsPlus: Boolean get() {
    return this.indicators.any { it == PCSC_SDS_PLUS }
  }

  companion object {
    const val SCHEDULE_15_LIFE_INDICATOR = "SCH15/CJIB/L"
    const val PCSC_SDS = "PCSC/SDS"
    const val PCSC_SEC250 = "PCSC/SEC250"
    const val PCSC_SDS_PLUS = "PCSC/SDS+"
  }
}
