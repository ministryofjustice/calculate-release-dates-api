package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions

import org.springframework.http.HttpStatus

class PreconditionFailedException(message: String) :
  CrdWebException(
    message,
    HttpStatus.PRECONDITION_FAILED,
  )
