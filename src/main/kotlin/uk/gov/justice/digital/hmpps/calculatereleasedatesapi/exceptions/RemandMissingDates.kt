package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions

import org.springframework.http.HttpStatus

// This exception occurs when a remand period doesn't have the 'to' or 'from' date set.
class RemandMissingDates(message: String) : CrdWebException(message, HttpStatus.UNPROCESSABLE_ENTITY, code = "REMAND_MISSING_DATES")
