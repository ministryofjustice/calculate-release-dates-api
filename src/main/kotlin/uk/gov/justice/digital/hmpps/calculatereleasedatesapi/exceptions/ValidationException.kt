package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions

import org.springframework.http.HttpStatus

/*
  This error is thrown when there is a validation error with the data from nomis for the prisoner.
  The frontend should validate before calling the calculation engine, so this error shouldn't be seen from the
  frontend.
 */
class ValidationException(message: String) : CrdWebException(message, HttpStatus.UNPROCESSABLE_ENTITY)
