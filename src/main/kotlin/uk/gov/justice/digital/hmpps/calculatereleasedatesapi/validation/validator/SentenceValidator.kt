package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.validator

import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.TermType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AbstractSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ExtendedDeterminateSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SopcSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.CalculationSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAndOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceTerms
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.ImportantDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.isAfterOrEqualTo
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.EDS18_EDS21_EDSU18_SENTENCE_TYPE_INCORRECT
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.LASPO_AR_SENTENCE_TYPE_INCORRECT
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.SEC236A_SENTENCE_TYPE_INCORRECT
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.SEC_91_SENTENCE_TYPE_INCORRECT
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.SOPC18_SOPC21_SENTENCE_TYPE_INCORRECT
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationMessage
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationOrder
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationUtilities
import java.time.Period

@Component
class SentenceValidator(private val validationUtilities: ValidationUtilities) : PreCalculationSourceDataValidator {

  override fun validate(sourceData: CalculationSourceData): List<ValidationMessage> = sourceData.sentenceAndOffences.map {
    listOfNotNull(
      validateWithoutOffenceDate(it),
      validateOffenceDateAfterSentenceDate(it),
      validateOffenceRangeDateAfterSentenceDate(it),
    ) + validateDuration(it) + listOfNotNull(
      section91Validation(it),
      edsSentenceValidation(it),
      validateFineAmount(it),
    ) + sopcSentenceValidation(it)
  }.flatten()

  private fun validateFineAmount(sentencesAndOffence: SentenceAndOffence): ValidationMessage? {
    if (isFineSentence(sentencesAndOffence) && sentencesAndOffence.fineAmount == null) {
      return ValidationMessage(
        ValidationCode.A_FINE_SENTENCE_MISSING_FINE_AMOUNT,
        validationUtilities.getCaseSeqAndLineSeq(sentencesAndOffence),
      )
    }
    return null
  }

  private fun isFineSentence(sentencesAndOffence: SentenceAndOffence): Boolean = SentenceCalculationType.from(sentencesAndOffence.sentenceCalculationType).sentenceType == SentenceType.AFine

  private fun sopcSentenceValidation(
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

  private fun edsSentenceValidation(sentenceAndOffence: SentenceAndOffence): ValidationMessage? {
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

  private fun section91Validation(sentencesAndOffence: SentenceAndOffence): ValidationMessage? {
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
  private fun validateOffenceRangeDateAfterSentenceDate(sentencesAndOffence: SentenceAndOffence): ValidationMessage? {
    val offence = sentencesAndOffence.offence
    val invalid = offence.offenceEndDate != null && offence.offenceEndDate > sentencesAndOffence.sentenceDate
    if (invalid) {
      return ValidationMessage(
        ValidationCode.OFFENCE_DATE_AFTER_SENTENCE_RANGE_DATE,
        validationUtilities.getCaseSeqAndLineSeq(sentencesAndOffence),
      )
    }
    return null
  }

  private fun validateDuration(sentencesAndOffence: SentenceAndOffence): List<ValidationMessage> {
    val sentenceCalculationType = SentenceCalculationType.from(sentencesAndOffence.sentenceCalculationType)
    val allowedTermTypes = sentenceCalculationType.sentenceType?.supportedTerms ?: emptySet()

    return if (allowedTermTypes == setOf(TermType.IMPRISONMENT)) {
      validateSingleTermDuration(sentencesAndOffence)
    } else {
      validateImprisonmentAndLicenceTermDuration(sentencesAndOffence)
    }
  }

  public fun validateWithoutOffenceDate(sentencesAndOffence: SentenceAndOffence): ValidationMessage? {
    // It's valid to not have an end date for many offence types, but the start date must always be present in
    // either case. If an end date is null it will be set to the start date in the transformation.
    val invalid = sentencesAndOffence.offence.offenceStartDate == null
    if (invalid) {
      return ValidationMessage(
        ValidationCode.OFFENCE_MISSING_DATE,
        validationUtilities.getCaseSeqAndLineSeq(sentencesAndOffence),
      )
    }
    return null
  }

  private fun validateOffenceDateAfterSentenceDate(
    sentencesAndOffence: SentenceAndOffence,
  ): ValidationMessage? {
    val offence = sentencesAndOffence.offence
    if (offence.offenceStartDate != null && offence.offenceStartDate > sentencesAndOffence.sentenceDate) {
      return ValidationMessage(
        ValidationCode.OFFENCE_DATE_AFTER_SENTENCE_START_DATE,
        validationUtilities.getCaseSeqAndLineSeq(sentencesAndOffence),
      )
    }
    return null
  }

  private fun validateSingleTermDuration(sentencesAndOffence: SentenceAndOffence): List<ValidationMessage> {
    val validationMessages = mutableListOf<ValidationMessage>()
    val hasMultipleTerms = sentencesAndOffence.terms.size > 1
    if (hasMultipleTerms) {
      validationMessages.add(
        ValidationMessage(
          ValidationCode.SENTENCE_HAS_MULTIPLE_TERMS,
          validationUtilities.getCaseSeqAndLineSeq(sentencesAndOffence),
        ),
      )
    } else {
      val emptyImprisonmentTerm =
        sentencesAndOffence.terms[0].days == 0 && sentencesAndOffence.terms[0].weeks == 0 && sentencesAndOffence.terms[0].months == 0 && sentencesAndOffence.terms[0].years == 0

      if (emptyImprisonmentTerm) {
        validationMessages.add(
          ValidationMessage(
            ValidationCode.ZERO_IMPRISONMENT_TERM,
            validationUtilities.getCaseSeqAndLineSeq(sentencesAndOffence),
          ),
        )
      }
    }
    return validationMessages
  }

  private fun validateImprisonmentAndLicenceTermDuration(sentencesAndOffence: SentenceAndOffence): List<ValidationMessage> {
    val validationMessages = mutableListOf<ValidationMessage>()

    val imprisonmentTerms = sentencesAndOffence.terms.filter { it.code == SentenceTerms.IMPRISONMENT_TERM_CODE }
    if (imprisonmentTerms.isEmpty()) {
      validationMessages.add(
        ValidationMessage(
          ValidationCode.SENTENCE_HAS_NO_IMPRISONMENT_TERM,
          validationUtilities.getCaseSeqAndLineSeq(sentencesAndOffence),
        ),
      )
    } else if (imprisonmentTerms.size > 1) {
      validationMessages.add(
        ValidationMessage(
          ValidationCode.MORE_THAN_ONE_IMPRISONMENT_TERM,
          validationUtilities.getCaseSeqAndLineSeq(sentencesAndOffence),
        ),
      )
    } else {
      val emptyTerm =
        imprisonmentTerms[0].days == 0 && imprisonmentTerms[0].weeks == 0 && imprisonmentTerms[0].months == 0 && imprisonmentTerms[0].years == 0
      if (emptyTerm) {
        validationMessages.add(
          ValidationMessage(
            ValidationCode.ZERO_IMPRISONMENT_TERM,
            validationUtilities.getCaseSeqAndLineSeq(sentencesAndOffence),
          ),
        )
      }
    }
    val licenceTerms = sentencesAndOffence.terms.filter { it.code == SentenceTerms.LICENCE_TERM_CODE }
    if (licenceTerms.isEmpty()) {
      validationMessages.add(
        ValidationMessage(
          ValidationCode.SENTENCE_HAS_NO_LICENCE_TERM,
          validationUtilities.getCaseSeqAndLineSeq(sentencesAndOffence),
        ),
      )
    } else if (licenceTerms.size > 1) {
      validationMessages.add(
        ValidationMessage(
          ValidationCode.MORE_THAN_ONE_LICENCE_TERM,
          validationUtilities.getCaseSeqAndLineSeq(sentencesAndOffence),
        ),
      )
    } else {
      val sentenceCalculationType = SentenceCalculationType.from(sentencesAndOffence.sentenceCalculationType)
      val typeClazz: Class<out AbstractSentence>? = sentenceCalculationType.sentenceType?.sentenceClazz
      if (typeClazz == ExtendedDeterminateSentence::class.java) {
        val duration =
          Period.of(
            licenceTerms[0].years,
            licenceTerms[0].months,
            licenceTerms[0].weeks * 7 + licenceTerms[0].days,
          )
        val endOfDuration = sentencesAndOffence.sentenceDate.plus(duration)
        val endOfOneYear = sentencesAndOffence.sentenceDate.plusYears(1)
        val endOfEightYears = sentencesAndOffence.sentenceDate.plusYears(8)

        if (endOfDuration.isBefore(endOfOneYear)) {
          validationMessages.add(
            ValidationMessage(
              ValidationCode.EDS_LICENCE_TERM_LESS_THAN_ONE_YEAR,
              validationUtilities.getCaseSeqAndLineSeq(sentencesAndOffence),
            ),
          )
        } else if (endOfDuration.isAfter(endOfEightYears)) {
          validationMessages.add(
            ValidationMessage(
              ValidationCode.EDS_LICENCE_TERM_MORE_THAN_EIGHT_YEARS,
              validationUtilities.getCaseSeqAndLineSeq(sentencesAndOffence),
            ),
          )
        }
      } else if (typeClazz == SopcSentence::class.java) {
        val duration =
          Period.of(
            licenceTerms[0].years,
            licenceTerms[0].months,
            licenceTerms[0].weeks * 7 + licenceTerms[0].days,
          )
        val endOfDuration = sentencesAndOffence.sentenceDate.plus(duration)
        val endOfOneYear = sentencesAndOffence.sentenceDate.plusYears(1)
        if (endOfDuration != endOfOneYear) {
          validationMessages.add(
            ValidationMessage(
              ValidationCode.SOPC_LICENCE_TERM_NOT_12_MONTHS,
              validationUtilities.getCaseSeqAndLineSeq(sentencesAndOffence),
            ),
          )
        }
      }
    }

    return validationMessages
  }

  override fun validationOrder() = ValidationOrder.INVALID
}
