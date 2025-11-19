package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.FeatureToggles
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationUserInputs

data class CalculationTestFile(
  val booking: Booking,
  val userInputs: CalculationUserInputs = CalculationUserInputs(),
  val error: String? = null,
  val params: String = "calculation-params",
  val assertSds40: Boolean? = false,
  val expectedValidationException: String? = null,
  val expectedValidationMessage: String? = null,
  val featureToggles: FeatureToggles? = null,
  val previousCalculations: List<TestPreviousCalculation>? = null,
)
