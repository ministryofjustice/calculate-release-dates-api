package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AFineSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonApiSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAndOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType

@Service
class FineValidationService(private val validationUtilities: ValidationUtilities) {

  fun validateFineSentenceSupported(prisonApiSourceData: PrisonApiSourceData): List<ValidationMessage> {
    val validationMessages = mutableListOf<ValidationMessage>()
    val fineSentences =
      prisonApiSourceData.sentenceAndOffences.filter { SentenceCalculationType.from(it.sentenceCalculationType).sentenceClazz == AFineSentence::class.java }
    if (fineSentences.isNotEmpty()) {
      if (prisonApiSourceData.offenderFinePayments.isNotEmpty()) {
        validationMessages.add(ValidationMessage(ValidationCode.A_FINE_SENTENCE_WITH_PAYMENTS))
      }
      if (fineSentences.any { it.consecutiveToSequence != null }) {
        validationMessages.add(ValidationMessage(ValidationCode.A_FINE_SENTENCE_CONSECUTIVE_TO))
      }
      val sequenceToSentenceMap = prisonApiSourceData.sentenceAndOffences.associateBy { it.sentenceSequence }
      if (prisonApiSourceData.sentenceAndOffences.any {
          it.consecutiveToSequence != null && fineSentences.contains(sequenceToSentenceMap[(it.consecutiveToSequence)])
        }
      ) {
        validationMessages.add(ValidationMessage(ValidationCode.A_FINE_SENTENCE_CONSECUTIVE))
      }
    }
    return validationMessages
  }

  internal fun validateFineAmount(sentencesAndOffence: SentenceAndOffence): ValidationMessage? {
    val sentenceCalculationType = SentenceCalculationType.from(sentencesAndOffence.sentenceCalculationType)
    if (sentenceCalculationType.sentenceClazz == AFineSentence::class.java) {
      if (sentencesAndOffence.fineAmount == null) {
        return ValidationMessage(ValidationCode.A_FINE_SENTENCE_MISSING_FINE_AMOUNT, validationUtilities.getCaseSeqAndLineSeq(sentencesAndOffence))
      }
    }
    return null
  }
}
