package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions

import org.springframework.http.HttpStatus

/*
  This error is thrown when the results of a calculation have changed since last time. This will occur when a calculation
  is made and persisted to the database and a subsequent release of the calculation engine returns a different calculation,
  we can then no longer display the original breakdown
 */
class BreakdownChangedSinceLastCalculation(message: String) : CrdWebException(message, HttpStatus.UNPROCESSABLE_ENTITY)
