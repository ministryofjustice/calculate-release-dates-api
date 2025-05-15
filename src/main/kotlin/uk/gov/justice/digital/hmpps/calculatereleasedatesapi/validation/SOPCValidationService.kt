package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAndOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.ImportantDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.isAfterOrEqualTo
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.SEC236A_SENTENCE_TYPE_INCORRECT
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.SOPC18_SOPC21_SENTENCE_TYPE_INCORRECT

@Service
class SOPCValidationService(private val validationUtilities: ValidationUtilities) {

  internal fun validate(
    sentencesAndOffence: SentenceAndOffence,
  ): List<ValidationMessage> {
    val messages = mutableListOf<ValidationMessage>()
    messages.addAll(validateSOPC(sentencesAndOffence))
    messages.addAll(validateSec236A(sentencesAndOffence))
    return messages
  }

  private fun validateSOPC(sentencesAndOffence: SentenceAndOffence): List<ValidationMessage> {
    val messages = mutableListOf<ValidationMessage>()
    if (isSopc(SentenceCalculationType.from(sentencesAndOffence.sentenceCalculationType)) && isBeforeSec91EndDate(sentencesAndOffence)) {
      messages.add(
        ValidationMessage(
          SOPC18_SOPC21_SENTENCE_TYPE_INCORRECT,
          validationUtilities.getCaseSeqAndLineSeq(sentencesAndOffence),
        ),
      )
    }
    return messages
  }

  private fun validateSec236A(sentencesAndOffence: SentenceAndOffence): List<ValidationMessage> {
    val messages = mutableListOf<ValidationMessage>()
    if (isSec236A(SentenceCalculationType.from(sentencesAndOffence.sentenceCalculationType)) && isAfterOrEqualToSec91EndDate(sentencesAndOffence)) {
      messages.add(
        ValidationMessage(
          SEC236A_SENTENCE_TYPE_INCORRECT,
          validationUtilities.getCaseSeqAndLineSeq(sentencesAndOffence),
        ),
      )
    }
    return messages
  }

  private fun isSopc(sentenceCalculationType: SentenceCalculationType): Boolean = sentenceCalculationType == SentenceCalculationType.SOPC18 || sentenceCalculationType == SentenceCalculationType.SOPC21

  private fun isSec236A(sentenceCalculationType: SentenceCalculationType): Boolean = sentenceCalculationType == SentenceCalculationType.SEC236A

  private fun isBeforeSec91EndDate(sentencesAndOffence: SentenceAndOffence): Boolean = sentencesAndOffence.sentenceDate.isBefore(ImportantDates.SEC_91_END_DATE)

  private fun isAfterOrEqualToSec91EndDate(sentencesAndOffence: SentenceAndOffence): Boolean = sentencesAndOffence.sentenceDate.isAfterOrEqualTo(ImportantDates.SEC_91_END_DATE)
}
