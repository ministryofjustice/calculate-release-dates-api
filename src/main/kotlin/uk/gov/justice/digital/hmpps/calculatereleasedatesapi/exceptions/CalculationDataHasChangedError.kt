package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions

import org.springframework.http.HttpStatus

class CalculationDataHasChangedError(calculationReference: String, prisonerId: String) : CrdWebException("NOMIS data for calculation $calculationReference and prisoner $prisonerId has changed.", HttpStatus.INTERNAL_SERVER_ERROR)
