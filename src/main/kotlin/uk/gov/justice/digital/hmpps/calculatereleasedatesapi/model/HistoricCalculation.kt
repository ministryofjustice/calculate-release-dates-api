package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import com.fasterxml.jackson.annotation.JsonFormat
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationType
import java.time.LocalDateTime

data class HistoricCalculation(
  val offenderNo: String,
  @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss.SSSSSS")
  val calculationDate: LocalDateTime,
  val calculationSource: CalculationSource,
  val calculationViewConfiguration: CalculationViewConfiguration?,
  val commentText: String?,
  val calculationType: CalculationType?,
  val establishment: String?,
  val calculationRequestId: Long?,
  val calculationReason: String?,
  val offenderSentCalculationId: Long?,
  val genuineOverrideReasonCode: GenuineOverrideReason?,
  val genuineOverrideReasonDescription: String?,
  // TODO nullable until Prison API released to production
  val calculatedByUsername: String?,
  val calculatedByDisplayName: String?,
)
