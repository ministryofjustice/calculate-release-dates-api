package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.validator

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationOrder

interface Validator {
  fun validationOrder(): ValidationOrder
}
