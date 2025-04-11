package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAdjustmentType
import java.time.LocalDate

data class AnalysedSentenceAdjustment(
  val sentenceSequence: Int,
  val active: Boolean,
  val fromDate: LocalDate? = null,
  val toDate: LocalDate? = null,
  val numberOfDays: Int,
  val type: SentenceAdjustmentType,
  val analysisResult: AnalysedBookingAndSentenceAdjustmentAnalysisResult,
)
