package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions

import org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR

class CouldNotSaveDatesToNomisException(msg: String) : CrdWebException(msg, INTERNAL_SERVER_ERROR)
