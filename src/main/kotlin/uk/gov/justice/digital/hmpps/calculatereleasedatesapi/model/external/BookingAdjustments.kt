package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external

import java.time.LocalDate

data class BookingAdjustments(
  val active: Boolean,
  val fromDate: LocalDate,
  val toDate: LocalDate? = null,
  val numberOfDays: Int,
  val type: BookingAdjustmentType
)

enum class BookingAdjustmentType {
  ADDITIONAL_DAYS_AWARDED,
  LAWFULLY_AT_LARGE,
  RESTORED_ADDITIONAL_DAYS_AWARDED,
  SPECIAL_REMISSION,
  UNLAWFULLY_AT_LARGE;
}
