package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.CalculationSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAndOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType

@Service
class FineValidationService(private val validationUtilities: ValidationUtilities) {

  fun validateFineSentenceSupported(calculationSourceData: CalculationSourceData): List<ValidationMessage> {
    val fineSentences = getFineSentences(calculationSourceData)

    if (fineSentences.isEmpty()) {
      return emptyList()
    }

    val validationMessages = mutableListOf<ValidationMessage>()

    if (hasFinePayments(calculationSourceData)) {
      validationMessages.add(ValidationMessage(ValidationCode.A_FINE_SENTENCE_WITH_PAYMENTS))
    }

    if (hasConsecutiveFineSentence(fineSentences)) {
      validationMessages.add(ValidationMessage(ValidationCode.A_FINE_SENTENCE_CONSECUTIVE_TO))
    }

    if (hasConsecutiveToFineSentence(calculationSourceData, fineSentences)) {
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

  internal fun validateFineAmount(sentencesAndOffence: SentenceAndOffence): ValidationMessage? {
    if (isFineSentence(sentencesAndOffence) && sentencesAndOffence.fineAmount == null) {
      return ValidationMessage(
        ValidationCode.A_FINE_SENTENCE_MISSING_FINE_AMOUNT,
        validationUtilities.getCaseSeqAndLineSeq(sentencesAndOffence),
      )
    }
    return null
  }

  private fun isFineSentence(sentencesAndOffence: SentenceAndOffence): Boolean = SentenceCalculationType.from(sentencesAndOffence.sentenceCalculationType).sentenceType == SentenceType.AFine
}
