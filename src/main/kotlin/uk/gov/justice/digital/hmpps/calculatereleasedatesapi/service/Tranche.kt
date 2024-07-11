package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationResult

@Suppress("ktlint:standard:filename")
interface Tranche {
  fun isBookingApplicableForTrancheCriteria(calculationResult: CalculationResult, booking: Booking): Boolean
}
