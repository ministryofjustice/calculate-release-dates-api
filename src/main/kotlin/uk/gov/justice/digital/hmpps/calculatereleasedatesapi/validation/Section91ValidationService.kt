package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAndOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.ImportantDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.isAfterOrEqualTo
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.SEC_91_SENTENCE_TYPE_INCORRECT

@Service
class Section91ValidationService(private val validationUtilities: ValidationUtilities) {

  internal fun validateThatSec91SentenceTypeCorrectlyApplied(sentencesAndOffence: SentenceAndOffence): ValidationMessage? {
    val sentenceCalculationType = SentenceCalculationType.from(sentencesAndOffence.sentenceCalculationType)

    if (sentenceCalculationType == SentenceCalculationType.SEC91_03 || sentenceCalculationType == SentenceCalculationType.SEC91_03_ORA) {
      if (sentencesAndOffence.sentenceDate.isAfterOrEqualTo(ImportantDates.SEC_91_END_DATE)) {
        return ValidationMessage(SEC_91_SENTENCE_TYPE_INCORRECT, validationUtilities.getCaseSeqAndLineSeq(sentencesAndOffence))
      }
    }
    return null
  }
}
