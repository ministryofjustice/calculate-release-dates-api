package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions

import org.springframework.http.HttpStatus

/*
   This exception is thrown when the prison api data for a historic calculation cannot be
 */
class PrisonApiDataNotFoundException(message: String) : CrdWebException(message, HttpStatus.NOT_FOUND, code = "PRISON_API_DATA_MISSING")
