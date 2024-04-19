package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions

import org.springframework.http.HttpStatus

class UnsupportedCalculationBreakdown(message: String) : CrdWebException(message, HttpStatus.BAD_REQUEST)
