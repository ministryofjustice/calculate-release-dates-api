package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions

import org.springframework.http.HttpStatus

class SentenceGroupNotFound(msg: String) : CrdWebException(msg, HttpStatus.NOT_FOUND)
