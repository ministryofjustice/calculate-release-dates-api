package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationMessage

data class GenuineOverrideCreatedResponse(
  val success: Boolean,
  val newCalculationRequestId: Long? = null,
  val originalCalculationRequestId: Long? = null,
  val validationMessages: List<ValidationMessage>? = null,
)
