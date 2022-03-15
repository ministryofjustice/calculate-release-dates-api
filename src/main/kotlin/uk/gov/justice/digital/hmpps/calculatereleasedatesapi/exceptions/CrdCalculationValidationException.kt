package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions

import org.springframework.http.HttpStatus
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode

open class CrdCalculationValidationException(
  override var message: String,
  var validation: ValidationCode
) : CrdWebException(message, HttpStatus.UNPROCESSABLE_ENTITY, validation.name)
