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

  internal fun validateSopcSentenceTypesCorrectlyApplied(sentencesAndOffence: SentenceAndOffence): ValidationMessage? {
    val sentenceCalculationType = SentenceCalculationType.from(sentencesAndOffence.sentenceCalculationType)

    if (listOf(SentenceCalculationType.SOPC18, SentenceCalculationType.SOPC21).contains(sentenceCalculationType)) {
      if (sentencesAndOffence.sentenceDate.isBefore(ImportantDates.SEC_91_END_DATE)) {
        return ValidationMessage(
          SOPC18_SOPC21_SENTENCE_TYPE_INCORRECT,
          validationUtilities.getCaseSeqAndLineSeq(sentencesAndOffence),
        )
      }
    } else if (sentenceCalculationType == SentenceCalculationType.SEC236A) {
      if (sentencesAndOffence.sentenceDate.isAfterOrEqualTo(ImportantDates.SEC_91_END_DATE)) {
        return ValidationMessage(SEC236A_SENTENCE_TYPE_INCORRECT, validationUtilities.getCaseSeqAndLineSeq(sentencesAndOffence))
      }
    }
    return null
  }
}
