package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationUserInputs

data class CalculationTestFile(
  val booking: Booking,
  val userInputs: CalculationUserInputs = CalculationUserInputs(),
  val error: String? = null,
  val params: String? = null,
  val assertSds40: Boolean? = false,
  val expectedValidationException: String? = null,
  val expectedValidationMessage: String? = null,
  val featureTogglesStr: String? = null,
)
