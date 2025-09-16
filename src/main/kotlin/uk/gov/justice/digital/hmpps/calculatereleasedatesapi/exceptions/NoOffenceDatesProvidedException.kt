package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions

import org.springframework.http.HttpStatus

class NoOffenceDatesProvidedException(message: String) :
  CrdWebException(
    message,
    HttpStatus.UNPROCESSABLE_ENTITY,
  )
