package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationStatus
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import java.time.LocalDate
import java.time.Period

data class CalculatedReleaseDates(
  val dates: Map<ReleaseDateType, LocalDate>,
  val calculationRequestId: Long,
  val bookingId: Long,
  val prisonerId: String,
  val calculationStatus: CalculationStatus,
  val calculationFragments: CalculationFragments? = null,
  // TODO. This needs refactoring. The effectiveSentenceLength comes out of the calculation engine, but its not stored.
  // Its required to be sent to NOMIS, but not required for our API.
  val effectiveSentenceLength: Period? = null
)
