package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationStatus
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import java.time.LocalDate

data class TestPreviousCalculation(
  val calculationType: CalculationType,
  val calculationStatus: CalculationStatus,
  val calculationDate: LocalDate,
  val dates: Map<ReleaseDateType, LocalDate>,
)
