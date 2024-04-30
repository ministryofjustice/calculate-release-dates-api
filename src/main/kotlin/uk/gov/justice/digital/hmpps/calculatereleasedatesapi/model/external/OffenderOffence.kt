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
  val indicators: List<String> = emptyList(),
)
