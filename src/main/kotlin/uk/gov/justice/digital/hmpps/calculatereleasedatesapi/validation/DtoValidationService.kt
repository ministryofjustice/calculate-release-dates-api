package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.CalculationSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAndOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceTerms
@Service
class DtoValidationService {

  internal fun validate(calculationSourceData: CalculationSourceData): List<ValidationMessage> {
    val messages = mutableListOf<ValidationMessage>()
    messages.addAll(validateDtoIsNotRecall(calculationSourceData))
    messages.addAll(validateDtoIsNotConsecutiveToSentence(calculationSourceData))
    return messages
  }

  private fun validateDtoIsNotRecall(calculationSourceData: CalculationSourceData): List<ValidationMessage> {
    val messages = mutableListOf<ValidationMessage>()
    var bookingHasDto = false
    var bookingHasRecall = false

    calculationSourceData.sentenceAndOffences.forEach { sentenceAndOffence ->
      val sentenceCalculationType = SentenceCalculationType.from(sentenceAndOffence.sentenceCalculationType)
      val hasDtoRecall = hasDtoRecallTerms(sentenceAndOffence)
      val hasDto = SentenceCalculationType.isDTOType(sentenceAndOffence.sentenceCalculationType)

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

  private fun validateDtoIsNotConsecutiveToSentence(calculationSourceData: CalculationSourceData): List<ValidationMessage> {
    val messages = mutableListOf<ValidationMessage>()

    calculationSourceData.sentenceAndOffences.forEach { sentenceAndOffence ->
      if (SentenceCalculationType.isDTOType(sentenceAndOffence.sentenceCalculationType)) {
        if (isConsecutiveToNonDto(sentenceAndOffence, calculationSourceData)) {
          messages.add(ValidationMessage(code = ValidationCode.DTO_CONSECUTIVE_TO_SENTENCE))
        }
        if (hasNonDtoConsecutiveToIt(sentenceAndOffence, calculationSourceData)) {
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

  private fun isConsecutiveToNonDto(sentenceAndOffence: SentenceAndOffence, sourceData: CalculationSourceData): Boolean {
    return sentenceAndOffence.consecutiveToSequence != null && sequenceNotDto(sentenceAndOffence.consecutiveToSequence!!, sourceData)
  }

  private fun hasNonDtoConsecutiveToIt(sentenceAndOffence: SentenceAndOffence, sourceData: CalculationSourceData): Boolean {
    return sourceData.sentenceAndOffences.any {
      it.consecutiveToSequence == sentenceAndOffence.sentenceSequence &&
        SentenceCalculationType.from(it.sentenceCalculationType).sentenceType != SentenceType.DetentionAndTrainingOrder
    }
  }

  private fun sequenceNotDto(consecutiveSequence: Int, sourceData: CalculationSourceData): Boolean {
    val consecutiveTo = sourceData.sentenceAndOffences.firstOrNull { it.sentenceSequence == consecutiveSequence }
    return consecutiveTo != null && SentenceCalculationType.from(consecutiveTo.sentenceCalculationType).sentenceType != SentenceType.DetentionAndTrainingOrder
  }
}
