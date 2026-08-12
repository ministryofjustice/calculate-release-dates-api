package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.validator

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.threeten.extra.LocalDateRange
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationMessage
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationOrder

@Component
class UalValidator(
  @Value($$"${adjustments.ui.url}") private val adjustmentsUiUrl: String,
) : PreCalculationBookingValidator {

  override fun validate(booking: Booking): List<ValidationMessage> {
    val ual = booking.adjustments.getOrEmptyList(AdjustmentType.UNLAWFULLY_AT_LARGE)
    if (ual.isEmpty()) {
      return emptyList()
    }
    val messages = mutableListOf<ValidationMessage>()
    val ualRanges = ual
      .filter { it.fromDate != null && it.toDate != null }
      .mapIndexed { index, adjustment -> index to LocalDateRange.of(adjustment.fromDate!!, adjustment.toDate!!) }
    if (ualRanges.any { (index, ual) -> ualRanges.any { (otherIndex, otherUal) -> index != otherIndex && ual.isConnected(otherUal) } }) {
      messages += ValidationMessage(ValidationCode.DUPLICATE_OR_OVERLAPPING_UAL, listOf(adjustmentsUiUrl, booking.offender.reference))
    }
    return messages
  }

  override fun validationOrder() = ValidationOrder.INVALID
}
