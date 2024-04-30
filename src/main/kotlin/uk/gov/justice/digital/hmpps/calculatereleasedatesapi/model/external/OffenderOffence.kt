package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.time.LocalDate

@JsonIgnoreProperties(ignoreUnknown = true)
data class OffenderOffence(
  val offenderChargeId: Long,
  val offenceStartDate: LocalDate?,
  val offenceEndDate: LocalDate? = null,
  val offenceCode: String,
  val offenceDescription: String,
  var indicators: List<String> = listOf(),
) {

  companion object {
    const val SCHEDULE_15_LIFE_INDICATOR = "SCH15/CJIB/L"
    const val PCSC_SDS = "PCSC/SDS"
    const val PCSC_SEC250 = "PCSC/SEC250"
    const val PCSC_SDS_PLUS = "PCSC/SDS+"
  }
}
