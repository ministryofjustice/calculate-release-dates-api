package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import com.fasterxml.jackson.annotation.JsonFormat
import java.time.LocalDateTime

data class HistoricCalculation(
  @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss.SSSSSS")
  val calculationDate: LocalDateTime,
  val calculationSource: CalculationSource,
  val calculationViewConfiguration: CalculationViewConfiguration?,
  val commentText: String,
)
