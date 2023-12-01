package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationMessage

data class UnusedDeductionCalculationResponse(
  val unusedDeductions: Int?,
  val validationMessages: List<ValidationMessage> = emptyList()
)
