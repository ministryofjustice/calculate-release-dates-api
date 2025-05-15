package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAndOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.ImportantDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.isAfterOrEqualTo
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.SEC_91_SENTENCE_TYPE_INCORRECT

@Service
class Section91ValidationService(private val validationUtilities: ValidationUtilities) {

  internal fun validate(sentencesAndOffence: SentenceAndOffence): ValidationMessage? {
    val sentenceCalculationType = SentenceCalculationType.from(sentencesAndOffence.sentenceCalculationType)

    if (isNotSec91SentenceType(sentenceCalculationType)) {
      return null
    }

    return if (isAfterSec91EndDate(sentencesAndOffence)) {
      ValidationMessage(
        SEC_91_SENTENCE_TYPE_INCORRECT,
        validationUtilities.getCaseSeqAndLineSeq(sentencesAndOffence),
      )
    } else {
      null
    }
  }

  private fun isNotSec91SentenceType(sentenceCalculationType: SentenceCalculationType): Boolean = sentenceCalculationType != SentenceCalculationType.SEC91_03 &&
    sentenceCalculationType != SentenceCalculationType.SEC91_03_ORA

  private fun isAfterSec91EndDate(sentencesAndOffence: SentenceAndOffence): Boolean = sentencesAndOffence.sentenceDate.isAfterOrEqualTo(ImportantDates.SEC_91_END_DATE)
}
