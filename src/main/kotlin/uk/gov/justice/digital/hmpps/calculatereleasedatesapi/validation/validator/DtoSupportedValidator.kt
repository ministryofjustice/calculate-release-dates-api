package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.validator

import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.CalculationSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAndOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceTerms
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationMessage
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationOrder

@Component
class DtoSupportedValidator : PreCalculationSourceDataValidator {

  override fun validate(sourceData: CalculationSourceData): List<ValidationMessage> {
    val messages = mutableListOf<ValidationMessage>()
    messages.addAll(validateDtoIsNotRecall(sourceData))
    messages.addAll(validateDtoIsNotConsecutiveToSentence(sourceData))
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

  private fun hasDtoRecallTerms(sentenceAndOffence: SentenceAndOffence): Boolean = sentenceAndOffence.terms.any {
    it.code == SentenceTerms.BREACH_OF_SUPERVISION_REQUIREMENTS_TERM_CODE || it.code == SentenceTerms.BREACH_DUE_TO_IMPRISONABLE_OFFENCE_TERM_CODE
  }

  private fun isConsecutiveToNonDto(
    sentenceAndOffence: SentenceAndOffence,
    sourceData: CalculationSourceData,
  ): Boolean = sentenceAndOffence.consecutiveToSequence != null &&
    sequenceNotDto(
      sentenceAndOffence.consecutiveToSequence!!,
      sourceData,
    )

  private fun hasNonDtoConsecutiveToIt(
    sentenceAndOffence: SentenceAndOffence,
    sourceData: CalculationSourceData,
  ): Boolean = sourceData.sentenceAndOffences.any {
    it.consecutiveToSequence == sentenceAndOffence.sentenceSequence &&
      SentenceCalculationType.from(it.sentenceCalculationType).sentenceType != SentenceType.DetentionAndTrainingOrder
  }

  private fun sequenceNotDto(consecutiveSequence: Int, sourceData: CalculationSourceData): Boolean {
    val consecutiveTo = sourceData.sentenceAndOffences.firstOrNull { it.sentenceSequence == consecutiveSequence }
    return consecutiveTo != null && SentenceCalculationType.from(consecutiveTo.sentenceCalculationType).sentenceType != SentenceType.DetentionAndTrainingOrder
  }

  override fun validationOrder() = ValidationOrder.UNSUPPORTED
}
