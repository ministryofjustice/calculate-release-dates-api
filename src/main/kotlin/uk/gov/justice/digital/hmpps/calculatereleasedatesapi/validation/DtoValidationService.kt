package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.DetentionAndTrainingOrderSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonApiSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType.DTO
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType.DTO_ORA
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceTerms

@Service
class DtoValidationService {

  internal fun validateDtoIsNotRecall(prisonApiSourceData: PrisonApiSourceData): List<ValidationMessage> {
    val validationMessages = mutableListOf<ValidationMessage>()
    var bookingHasDto = false
    var bookingHasRecall = false
    prisonApiSourceData.sentenceAndOffences.forEach {
      val sentenceCalculationType = SentenceCalculationType.from(it.sentenceCalculationType)
      val hasDtoRecall = it.terms.any { terms ->
        terms.code == SentenceTerms.BREACH_OF_SUPERVISION_REQUIREMENTS_TERM_CODE || terms.code == SentenceTerms.BREACH_DUE_TO_IMPRISONABLE_OFFENCE_TERM_CODE
      }
      val hasDto = sentenceCalculationType == DTO || sentenceCalculationType == DTO_ORA
      if (hasDto) {
        bookingHasDto = true
      }
      if (sentenceCalculationType.recallType != null) {
        bookingHasRecall = true
      }
      if (hasDto && hasDtoRecall) {
        validationMessages.add(ValidationMessage(ValidationCode.DTO_RECALL))
      } else if (bookingHasDto && bookingHasRecall) {
        validationMessages.add(ValidationMessage(ValidationCode.UNSUPPORTED_CALCULATION_DTO_WITH_RECALL))
      }
    }

    return validationMessages.toList()
  }

  internal fun validateDtoIsNotConsecutiveToSentence(sourceData: PrisonApiSourceData): List<ValidationMessage> {
    val validationMessages = mutableListOf<ValidationMessage>()
    sourceData.sentenceAndOffences.forEach {
      val isDto =
        SentenceCalculationType.from(it.sentenceCalculationType).sentenceClazz == DetentionAndTrainingOrderSentence::class.java
      if (isDto) {
        if (it.consecutiveToSequence != null && sequenceNotDto(it.consecutiveToSequence, sourceData)) {
          validationMessages.add(ValidationMessage(code = ValidationCode.DTO_CONSECUTIVE_TO_SENTENCE))
        }
        if (sourceData.sentenceAndOffences.any { sent ->
            (
              sent.consecutiveToSequence == it.sentenceSequence && SentenceCalculationType.from(
                sent.sentenceCalculationType,
              ).sentenceClazz != DetentionAndTrainingOrderSentence::class.java
              )
          }
        ) {
          validationMessages.add(ValidationMessage(code = ValidationCode.DTO_HAS_SENTENCE_CONSECUTIVE_TO_IT))
        }
      }
    }

    return validationMessages.toList()
  }

  private fun sequenceNotDto(consecutiveSequence: Int, sourceData: PrisonApiSourceData): Boolean {
    val consecutiveTo = sourceData.sentenceAndOffences.firstOrNull { it.sentenceSequence == consecutiveSequence }
    return consecutiveTo != null && SentenceCalculationType.from(consecutiveTo.sentenceCalculationType).sentenceClazz != DetentionAndTrainingOrderSentence::class.java
  }
}
