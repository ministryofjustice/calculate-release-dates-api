package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.DetentionAndTrainingOrderSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonApiSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAndOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType.DTO
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType.DTO_ORA
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceTerms
@Service
class DtoValidationService {

  internal fun validate(prisonApiSourceData: PrisonApiSourceData): List<ValidationMessage> {
    val messages = mutableListOf<ValidationMessage>()
    messages.addAll(validateDtoIsNotRecall(prisonApiSourceData))
    messages.addAll(validateDtoIsNotConsecutiveToSentence(prisonApiSourceData))
    return messages
  }

  private fun validateDtoIsNotRecall(prisonApiSourceData: PrisonApiSourceData): List<ValidationMessage> {
    val messages = mutableListOf<ValidationMessage>()
    var bookingHasDto = false
    var bookingHasRecall = false

    prisonApiSourceData.sentenceAndOffences.forEach { sentenceAndOffence ->
      val sentenceCalculationType = SentenceCalculationType.from(sentenceAndOffence.sentenceCalculationType)
      val hasDtoRecall = hasDtoRecallTerms(sentenceAndOffence)
      val hasDto = isDtoSentence(sentenceCalculationType)

      if (hasDto) bookingHasDto = true
      if (sentenceCalculationType.recallType != null) bookingHasRecall = true

      if (hasDto && hasDtoRecall) {
        messages.add(ValidationMessage(ValidationCode.UNSUPPORTED_DTO_RECALL_SEC104_SEC105))
      } else if (bookingHasDto && bookingHasRecall) {
        messages.add(ValidationMessage(ValidationCode.UNSUPPORTED_CALCULATION_DTO_WITH_RECALL))
      }
    }

    return messages
  }

  private fun validateDtoIsNotConsecutiveToSentence(prisonApiSourceData: PrisonApiSourceData): List<ValidationMessage> {
    val messages = mutableListOf<ValidationMessage>()

    prisonApiSourceData.sentenceAndOffences.forEach { sentenceAndOffence ->
      if (isDtoSentence(SentenceCalculationType.from(sentenceAndOffence.sentenceCalculationType))) {
        if (isConsecutiveToNonDto(sentenceAndOffence, prisonApiSourceData)) {
          messages.add(ValidationMessage(code = ValidationCode.DTO_CONSECUTIVE_TO_SENTENCE))
        }
        if (hasNonDtoConsecutiveToIt(sentenceAndOffence, prisonApiSourceData)) {
          messages.add(ValidationMessage(code = ValidationCode.DTO_HAS_SENTENCE_CONSECUTIVE_TO_IT))
        }
      }
    }

    return messages
  }

  private fun hasDtoRecallTerms(sentenceAndOffence: SentenceAndOffence): Boolean {
    return sentenceAndOffence.terms.any {
      it.code == SentenceTerms.BREACH_OF_SUPERVISION_REQUIREMENTS_TERM_CODE || it.code == SentenceTerms.BREACH_DUE_TO_IMPRISONABLE_OFFENCE_TERM_CODE
    }
  }

  private fun isDtoSentence(sentenceCalculationType: SentenceCalculationType): Boolean {
    return sentenceCalculationType == DTO || sentenceCalculationType == DTO_ORA
  }

  private fun isConsecutiveToNonDto(sentenceAndOffence: SentenceAndOffence, sourceData: PrisonApiSourceData): Boolean {
    return sentenceAndOffence.consecutiveToSequence != null && sequenceNotDto(sentenceAndOffence.consecutiveToSequence!!, sourceData)
  }

  private fun hasNonDtoConsecutiveToIt(sentenceAndOffence: SentenceAndOffence, sourceData: PrisonApiSourceData): Boolean {
    return sourceData.sentenceAndOffences.any {
      it.consecutiveToSequence == sentenceAndOffence.sentenceSequence &&
        SentenceCalculationType.from(it.sentenceCalculationType).sentenceClazz != DetentionAndTrainingOrderSentence::class.java
    }
  }

  private fun sequenceNotDto(consecutiveSequence: Int, sourceData: PrisonApiSourceData): Boolean {
    val consecutiveTo = sourceData.sentenceAndOffences.firstOrNull { it.sentenceSequence == consecutiveSequence }
    return consecutiveTo != null && SentenceCalculationType.from(consecutiveTo.sentenceCalculationType).sentenceClazz != DetentionAndTrainingOrderSentence::class.java
  }
}
