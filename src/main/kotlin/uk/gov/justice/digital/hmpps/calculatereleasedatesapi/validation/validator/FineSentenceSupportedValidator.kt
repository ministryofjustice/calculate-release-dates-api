package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.validator

import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.CalculationSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAndOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationMessage
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationOrder
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationUtilities

@Component
class FineSentenceSupportedValidator(private val validationUtilities: ValidationUtilities) : PreCalculationSourceDataValidator {

  override fun validate(sourceData: CalculationSourceData): List<ValidationMessage> {
    val fineSentences = getFineSentences(sourceData)

    if (fineSentences.isEmpty()) {
      return emptyList()
    }

    val validationMessages = mutableListOf<ValidationMessage>()

    if (hasFinePayments(sourceData)) {
      validationMessages.add(ValidationMessage(ValidationCode.A_FINE_SENTENCE_WITH_PAYMENTS))
    }

    if (hasConsecutiveFineSentence(fineSentences)) {
      validationMessages.add(ValidationMessage(ValidationCode.A_FINE_SENTENCE_CONSECUTIVE_TO))
    }

    if (hasConsecutiveToFineSentence(sourceData, fineSentences)) {
      validationMessages.add(ValidationMessage(ValidationCode.A_FINE_SENTENCE_CONSECUTIVE))
    }

    return validationMessages
  }

  private fun getFineSentences(sourceData: CalculationSourceData): List<SentenceAndOffence> = sourceData.sentenceAndOffences.filter {
    SentenceCalculationType.from(it.sentenceCalculationType).sentenceType == SentenceType.AFine
  }

  private fun hasFinePayments(sourceData: CalculationSourceData): Boolean = sourceData.offenderFinePayments.isNotEmpty()

  private fun hasConsecutiveFineSentence(fineSentences: List<SentenceAndOffence>): Boolean = fineSentences.any { it.consecutiveToSequence != null }

  private fun hasConsecutiveToFineSentence(sourceData: CalculationSourceData, fineSentences: List<SentenceAndOffence>): Boolean {
    val sentenceSequenceMap: Map<Int?, SentenceAndOffence> = sourceData.sentenceAndOffences.associateBy { it.sentenceSequence }
    return sourceData.sentenceAndOffences.any {
      it.consecutiveToSequence != null && fineSentences.contains(sentenceSequenceMap[it.consecutiveToSequence])
    }
  }

  private fun isFineSentence(sentencesAndOffence: SentenceAndOffence): Boolean = SentenceCalculationType.from(sentencesAndOffence.sentenceCalculationType).sentenceType == SentenceType.AFine
  override fun validationOrder() = ValidationOrder.UNSUPPORTED
}
