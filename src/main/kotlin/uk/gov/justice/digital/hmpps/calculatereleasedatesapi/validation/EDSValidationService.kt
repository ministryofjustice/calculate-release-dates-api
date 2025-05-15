package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAndOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.ImportantDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.isAfterOrEqualTo
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.EDS18_EDS21_EDSU18_SENTENCE_TYPE_INCORRECT
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.LASPO_AR_SENTENCE_TYPE_INCORRECT

@Service
class EDSValidationService(private val validationUtilities: ValidationUtilities) {

  internal fun validate(sentenceAndOffence: SentenceAndOffence): ValidationMessage? {
    val sentenceCalculationType = SentenceCalculationType.from(sentenceAndOffence.sentenceCalculationType)

    return when {
      isEds(sentenceCalculationType) && isBeforeEdsStartDate(sentenceAndOffence) -> {
        createValidationMessage(EDS18_EDS21_EDSU18_SENTENCE_TYPE_INCORRECT, sentenceAndOffence)
      }
      isLaspo(sentenceCalculationType) && isAfterLaspoEndDate(sentenceAndOffence) -> {
        createValidationMessage(LASPO_AR_SENTENCE_TYPE_INCORRECT, sentenceAndOffence)
      }
      else -> null
    }
  }

  private fun isEds(type: SentenceCalculationType): Boolean = type in listOf(SentenceCalculationType.EDS18, SentenceCalculationType.EDS21, SentenceCalculationType.EDSU18)

  private fun isBeforeEdsStartDate(sentenceAndOffence: SentenceAndOffence): Boolean = sentenceAndOffence.sentenceDate.isBefore(ImportantDates.EDS18_SENTENCE_TYPES_START_DATE)

  private fun isLaspo(type: SentenceCalculationType): Boolean = type == SentenceCalculationType.LASPO_AR

  private fun isAfterLaspoEndDate(sentenceAndOffence: SentenceAndOffence): Boolean = sentenceAndOffence.sentenceDate.isAfterOrEqualTo(ImportantDates.LASPO_AR_SENTENCE_TYPES_END_DATE)

  private fun createValidationMessage(validationCode: ValidationCode, sentenceAndOffence: SentenceAndOffence): ValidationMessage = ValidationMessage(validationCode, validationUtilities.getCaseSeqAndLineSeq(sentenceAndOffence))
}
