package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationMessage
import java.time.LocalDate

data class RecordARecallValidationResult(
  val criticalValidationMessages: List<ValidationMessage>,
  val otherValidationMessages: List<ValidationMessage>,
  val earliestSentenceDate: LocalDate,
  val hasCriticalErrorsOnLatestBooking: Boolean = false,
)
