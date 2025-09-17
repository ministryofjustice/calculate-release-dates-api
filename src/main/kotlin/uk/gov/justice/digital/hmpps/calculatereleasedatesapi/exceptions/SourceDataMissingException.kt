package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions

import org.springframework.http.HttpStatus

class SourceDataMissingException(msg: String) : CrdWebException(msg, HttpStatus.INTERNAL_SERVER_ERROR)
