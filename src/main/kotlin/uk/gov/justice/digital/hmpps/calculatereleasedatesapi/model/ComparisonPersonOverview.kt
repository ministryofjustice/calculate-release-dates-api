package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import com.fasterxml.jackson.databind.JsonNode
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import java.time.LocalDate
import java.time.LocalDateTime

data class ComparisonPersonOverview(
  val personId: String,
  val isValid: Boolean,
  val isMatch: Boolean,
  val isActiveSexOffender: Boolean?,
  val validationMessages: JsonNode,
  val shortReference: String,
  val bookingId: Long,
  val calculatedAt: LocalDateTime,
  val crdsDates: Map<ReleaseDateType, LocalDate?>,
  val nomisDates: Map<ReleaseDateType, LocalDate?>,
  val overrideDates: Map<ReleaseDateType, LocalDate?>,
  val breakdownByReleaseDateType: Map<ReleaseDateType, ReleaseDateCalculationBreakdown>,
)
