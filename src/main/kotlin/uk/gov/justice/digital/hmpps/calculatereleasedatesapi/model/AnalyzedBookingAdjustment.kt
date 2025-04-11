package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BookingAdjustmentType
import java.time.LocalDate

data class AnalyzedBookingAdjustment(
  val active: Boolean,
  val fromDate: LocalDate,
  val toDate: LocalDate? = null,
  val numberOfDays: Int,
  val type: BookingAdjustmentType,
  val analysisResult: AnalyzedBookingAndSentenceAdjustmentAnalysisResult,
)
