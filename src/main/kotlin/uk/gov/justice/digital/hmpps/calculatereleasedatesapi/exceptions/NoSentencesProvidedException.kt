package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions

import org.springframework.http.HttpStatus

class NoSentencesProvidedException(message: String): CrdWebException(message, HttpStatus.UNPROCESSABLE_ENTITY)
