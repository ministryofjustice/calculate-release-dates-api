package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions

import org.springframework.http.HttpStatus

class GenuineOverrideNotFoundException(message: String) : CrdWebException(message, HttpStatus.NOT_FOUND)
