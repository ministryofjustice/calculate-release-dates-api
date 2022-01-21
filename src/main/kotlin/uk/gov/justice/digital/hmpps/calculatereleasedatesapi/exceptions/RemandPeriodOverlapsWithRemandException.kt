package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions

import org.springframework.http.HttpStatus

/*
  This exception occurs when a remand period overlaps with another remand period.
 */
class RemandPeriodOverlapsWithRemandException(message: String): CrdWebException(message, HttpStatus.UNPROCESSABLE_ENTITY, code = "REMAND_OVERLAPS_WITH_REMAND")
