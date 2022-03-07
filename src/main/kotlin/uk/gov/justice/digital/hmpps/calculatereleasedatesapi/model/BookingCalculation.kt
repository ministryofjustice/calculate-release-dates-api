package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import com.fasterxml.jackson.annotation.JsonIgnore
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import java.time.LocalDate
import java.time.Period

data class BookingCalculation(
  val dates: MutableMap<ReleaseDateType, LocalDate> = mutableMapOf(),
  var calculationRequestId: Long = -1,
  var effectiveSentenceLength: Period = Period.ZERO,
  var calculationFragments: CalculationFragments? = null,
  var bookingId: Long = -1,
  var prisonerId: String = "",
  @JsonIgnore
  var breakdownByReleaseDateType: Map<ReleaseDateType, ReleaseDateCalculationBreakdown> = mapOf()

)
