package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import com.fasterxml.jackson.annotation.JsonIgnore
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import java.time.LocalDate
import java.time.Period

data class BookingCalculation(
  val dates: MutableMap<ReleaseDateType, LocalDate> = mutableMapOf(),
  var calculationRequestId: Long = -1,
  var effectiveSentenceLength: Period = Period.ZERO,
  @JsonIgnore
  var daysAwardedServed: Long = 0,
  @JsonIgnore
  var breakdownByReleaseDateType: MutableMap<ReleaseDateType, ReleaseDateCalculationBreakdown> = mutableMapOf()
)
