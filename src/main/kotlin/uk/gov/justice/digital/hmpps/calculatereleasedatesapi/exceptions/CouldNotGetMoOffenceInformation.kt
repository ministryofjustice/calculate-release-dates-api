package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions

import org.springframework.http.HttpStatus

class CouldNotGetMoOffenceInformation(message: String) : CrdWebException(message, HttpStatus.INTERNAL_SERVER_ERROR)
