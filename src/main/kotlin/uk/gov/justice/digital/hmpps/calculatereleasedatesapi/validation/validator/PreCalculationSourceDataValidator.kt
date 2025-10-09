package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.validator

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.CalculationSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationMessage

interface PreCalculationSourceDataValidator : Validator {
  fun validate(sourceData: CalculationSourceData): List<ValidationMessage>
}
