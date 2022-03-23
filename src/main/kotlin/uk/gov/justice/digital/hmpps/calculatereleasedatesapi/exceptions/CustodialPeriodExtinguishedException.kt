package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode

class CustodialPeriodExtinguishedException(message: String, arguments: List<String>): CrdCalculationValidationException(message, ValidationCode.CUSTODIAL_PERIOD_EXTINGUISHED, arguments)