package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi

import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal
import java.time.LocalDate

class OffenderFinePaymentDto(
  @Schema(description = "The bookingId this payment relates to")
  var bookingId: Long? = null,

  @Schema(description = "Payment sequence - a unique identifier a payment on a booking")
  var sequence: Int? = null,

  @Schema(description = "The date of the payment")
  var paymentDate: LocalDate? = null,

  @Schema(description = "The amount of the payment")
  var paymentAmount: BigDecimal? = null,
)
