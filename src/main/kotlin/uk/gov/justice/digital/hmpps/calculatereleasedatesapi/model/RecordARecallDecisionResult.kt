package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.remandandsentencing.model.Recall
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationMessage
import java.time.LocalDate
import java.util.UUID

data class RecordARecallDecisionResult(
  val decision: RecordARecallDecision,
  val validationMessages: List<ValidationMessage> = emptyList(),
  val conflictingAdjustments: List<String> = emptyList(),
  val automatedCalculationData: AutomatedCalculationData? = null,
)

data class AutomatedCalculationData(
  val calculationRequestId: Long,
  val recallableSentences: List<RecallableSentence>,
  val expiredSentences: List<RecallableSentence>,
  val ineligibleSentences: List<RecallableSentence>,
  val sentencesBeforeInitialRelease: List<RecallableSentence>,
  val unexpectedRecallTypes: List<Recall.RecallType>,
)

data class RecallableSentence(
  val sentenceSequence: Int,
  val bookingId: Long,
  val uuid: UUID,
  val sentenceCalculation: RecallSentenceCalculation,
)

data class RecallSentenceCalculation(
  // The CRD calculated by CRDS.
  val conditionalReleaseDate: LocalDate,
  // The actual release date (given by external movements if exists)
  val actualReleaseDate: LocalDate,
  val licenseExpiry: LocalDate?,
)

enum class RecordARecallDecision {
  CRITICAL_ERRORS,
  AUTOMATED,
  NO_RECALLABLE_SENTENCES_FOUND,
  VALIDATION,
  CONFLICTING_ADJUSTMENTS,
}
