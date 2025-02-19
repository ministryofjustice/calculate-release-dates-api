package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions

import org.springframework.http.HttpStatus

class MissingTermException(message: String) : CrdWebException(message, HttpStatus.UNPROCESSABLE_ENTITY)
