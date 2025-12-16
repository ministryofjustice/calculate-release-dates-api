package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationReason
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationStatus
import java.time.LocalDate
import java.util.*

data class CalculationContext(
  val calculationRequestId: Long,
  val bookingId: Long,
  val prisonerId: String,
  val calculationStatus: CalculationStatus,
  val calculationReference: UUID,
  val calculationReason: CalculationReason?,
  val otherReasonDescription: String?,
  val calculationDate: LocalDate?,
  val calculationType: CalculationType,
  val genuineOverrideReasonCode: GenuineOverrideReason?,
  val genuineOverrideReasonDescription: String?,
  val usePreviouslyRecordedSLEDIfFound: Boolean,
  val calculatedByUsername: String,
  val calculatedByDisplayName: String,
)
