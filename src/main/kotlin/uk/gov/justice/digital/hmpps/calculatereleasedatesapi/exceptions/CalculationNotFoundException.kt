package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions

import org.springframework.http.HttpStatus

class CalculationNotFoundException(msg: String) : CrdWebException(msg, HttpStatus.CONFLICT)
