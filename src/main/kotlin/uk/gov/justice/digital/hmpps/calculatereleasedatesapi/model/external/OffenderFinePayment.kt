package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external

import java.math.BigDecimal
import java.time.LocalDate

data class OffenderFinePayment(
  val bookingId: Long,
  val paymentDate: LocalDate,
  val paymentAmount: BigDecimal,
)
