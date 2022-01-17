package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external

import java.time.LocalDate

data class OffenderOffence(
  val offenderChargeId: Long,
  val offenceStartDate: LocalDate,
  val offenceEndDate: LocalDate? = null,
  val offenceCode: String,
  val offenceDescription: String,
  var indicators: List<String> = listOf()
) {
  companion object {
    const val SCHEDULE_15_INDICATOR = "S15/CJIB"
  }
}
