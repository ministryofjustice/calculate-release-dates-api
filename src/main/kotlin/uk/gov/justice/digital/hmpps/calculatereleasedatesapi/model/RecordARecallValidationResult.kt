package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationMessage
import java.time.LocalDate

data class RecordARecallValidationResult(
  val latestCriticalMessages: List<ValidationMessage> = emptyList(),
  val latestOtherMessages: List<ValidationMessage> = emptyList(),
  val penultimateCriticalMessages: List<ValidationMessage> = emptyList(),
  val penultimateOtherMessages: List<ValidationMessage> = emptyList(),
  val earliestSentenceDate: LocalDate,
) {
  companion object {
    fun fromLatest(result: RecallInterimValidationResult): RecordARecallValidationResult = RecordARecallValidationResult(
      latestCriticalMessages = result.criticalMessages,
      latestOtherMessages = result.otherMessages,
      earliestSentenceDate = result.earliestSentenceDate,
    )

    fun fromLatestAndPenultimate(
      latest: RecallInterimValidationResult,
      penultimate: RecallInterimValidationResult,
    ): RecordARecallValidationResult = RecordARecallValidationResult(
      latestCriticalMessages = latest.criticalMessages,
      latestOtherMessages = latest.otherMessages,
      penultimateCriticalMessages = penultimate.criticalMessages,
      penultimateOtherMessages = penultimate.otherMessages,
      earliestSentenceDate = minOf(penultimate.earliestSentenceDate, latest.earliestSentenceDate),
    )
  }
}

data class RecallInterimValidationResult(
  val criticalMessages: List<ValidationMessage>,
  val otherMessages: List<ValidationMessage>,
  val earliestSentenceDate: LocalDate,
)
