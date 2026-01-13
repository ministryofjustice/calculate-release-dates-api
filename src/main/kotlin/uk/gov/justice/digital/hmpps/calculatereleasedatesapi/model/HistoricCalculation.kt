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
  val establishment: String?, // this is the establishment of the booking not of when the calculation was performed.
  val calculationRequestId: Long?,
  val calculationReason: String?,
  val offenderSentCalculationId: Long?,
  val genuineOverrideReasonCode: GenuineOverrideReason?,
  val genuineOverrideReasonDescription: String?,
  val calculatedByUsername: String,
  val calculatedByDisplayName: String,
)
