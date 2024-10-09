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

  internal fun validateEdsSentenceTypesCorrectlyApplied(sentencesAndOffence: SentenceAndOffence): ValidationMessage? {
    val sentenceCalculationType = SentenceCalculationType.from(sentencesAndOffence.sentenceCalculationType)

    if (listOf(
        SentenceCalculationType.EDS18,
        SentenceCalculationType.EDS21,
        SentenceCalculationType.EDSU18,
      ).contains(
        sentenceCalculationType,
      )
    ) {
      if (sentencesAndOffence.sentenceDate.isBefore(ImportantDates.EDS18_SENTENCE_TYPES_START_DATE)) {
        return ValidationMessage(
          EDS18_EDS21_EDSU18_SENTENCE_TYPE_INCORRECT,
          validationUtilities.getCaseSeqAndLineSeq(sentencesAndOffence),
        )
      }
    } else if (sentenceCalculationType == SentenceCalculationType.LASPO_AR) {
      if (sentencesAndOffence.sentenceDate.isAfterOrEqualTo(ImportantDates.LASPO_AR_SENTENCE_TYPES_END_DATE)) {
        return ValidationMessage(LASPO_AR_SENTENCE_TYPE_INCORRECT, validationUtilities.getCaseSeqAndLineSeq(sentencesAndOffence))
      }
    }
    return null
  }
}
