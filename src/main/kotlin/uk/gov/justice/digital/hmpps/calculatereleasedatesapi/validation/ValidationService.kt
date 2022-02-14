package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BookingAndSentenceAdjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAdjustmentType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAdjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAndOffences
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.PrisonService

@Service
class ValidationService(
  private val prisonService: PrisonService
) {

  private val supportedSentences = listOf(
    "ADIMP",
    "ADIMP_ORA",
    "YOI",
    "YOI_ORA",
    "SEC91_03",
    "SEC91_03_ORA",
    "SEC250",
    "SEC250_ORA"
  )

  fun validate(prisonerId: String): ValidationMessages {
    val prisonerDetails = prisonService.getOffenderDetail(prisonerId)
    val sentencesAndOffences = prisonService.getSentencesAndOffences(prisonerDetails.bookingId)
    val adjustments = prisonService.getBookingAndSentenceAdjustmentss(prisonerDetails.bookingId)
    return validate(sentencesAndOffences, adjustments)
  }

  fun validate(sentencesAndOffences: List<SentenceAndOffences>, adjustments: BookingAndSentenceAdjustments): ValidationMessages {
    val sortedSentences = sentencesAndOffences.sortedWith(this::sortByCaseNumberAndLineSequence)

    val unsupportedValidationMessages = validateSupportedSentences(sortedSentences)
    if (unsupportedValidationMessages.isNotEmpty()) {
      return ValidationMessages(ValidationType.UNSUPPORTED, unsupportedValidationMessages)
    }

    val validationMessages = sortedSentences.map { validateSentence(it) }.flatten().toMutableList()
    validationMessages += validateAdjustments(adjustments)
    if (validationMessages.isNotEmpty()) {
      return ValidationMessages(ValidationType.VALIDATION, validationMessages)
    }

    return ValidationMessages(ValidationType.VALID)
  }

  private fun validateAdjustments(adjustments: BookingAndSentenceAdjustments): List<ValidationMessage> {
    return adjustments.sentenceAdjustments.mapNotNull { validateAdjustment(it) }
  }

  private fun validateAdjustment(sentenceAdjustment: SentenceAdjustments): ValidationMessage? {
    if (sentenceAdjustment.type == SentenceAdjustmentType.REMAND && (sentenceAdjustment.fromDate == null || sentenceAdjustment.toDate == null)) {
      return ValidationMessage("Remand missing from and to date", ValidationCode.REMAND_FROM_TO_DATES_REQUIRED)
    }
    return null
  }

  private fun validateSentence(it: SentenceAndOffences): List<ValidationMessage> {
    return listOfNotNull(
      validateWithoutOffenceDate(it),
      validateOffenceDateAfterSentenceDate(it),
      validateOffenceRangeDateAfterSentenceDate(it),
      validateDurationNotZero(it)
    )
  }

  private fun validateDurationNotZero(sentencesAndOffence: SentenceAndOffences): ValidationMessage? {
    val invalid = sentencesAndOffence.days == 0 &&
      sentencesAndOffence.weeks == 0 &&
      sentencesAndOffence.months == 0 &&
      sentencesAndOffence.years == 0
    if (invalid) {
      return ValidationMessage("Sentence has no duration", ValidationCode.SENTENCE_HAS_NO_DURATION, sentencesAndOffence.sentenceSequence)
    }
    return null
  }

  private fun validateOffenceRangeDateAfterSentenceDate(sentencesAndOffence: SentenceAndOffences): ValidationMessage? {
    val invalid = sentencesAndOffence.offences.any { it.offenceEndDate != null && it.offenceEndDate > sentencesAndOffence.sentenceDate }
    if (invalid) {
      return ValidationMessage("Offence date range shouldn't be after sentence date", ValidationCode.OFFENCE_DATE_AFTER_SENTENCE_RANGE_DATE, sentencesAndOffence.sentenceSequence)
    }
    return null
  }

  private fun validateWithoutOffenceDate(sentencesAndOffence: SentenceAndOffences): ValidationMessage? {
    val invalid = sentencesAndOffence.offences.any { it.offenceEndDate == null && it.offenceStartDate == null }
    if (invalid) {
      return ValidationMessage("The offence must have a date", ValidationCode.OFFENCE_MISSING_DATE, sentencesAndOffence.sentenceSequence)
    }
    return null
  }

  private fun sortByCaseNumberAndLineSequence(a: SentenceAndOffences, b: SentenceAndOffences): Int {
    if (a.caseSequence > b.caseSequence) return 1
    if (a.caseSequence < b.caseSequence) return -1
    return a.lineSequence - b.lineSequence
  }

  private fun validateSupportedSentences(sentencesAndOffences: List<SentenceAndOffences>): List<ValidationMessage> {
    return sentencesAndOffences.filter { !supportedSentences.contains(it.sentenceCalculationType) }
      .map { ValidationMessage("Unsupported sentence type ${it.sentenceTypeDescription}", ValidationCode.UNSUPPORTED_SENTENCE_TYPE, it.sentenceSequence, listOf(it.sentenceTypeDescription)) }
  }

  private fun validateOffenceDateAfterSentenceDate(
    sentencesAndOffence: SentenceAndOffences
  ): ValidationMessage? {
    val invalid = sentencesAndOffence.offences.any { it.offenceStartDate != null && it.offenceStartDate > sentencesAndOffence.sentenceDate }
    if (invalid) {
      return ValidationMessage("Offence date shouldn't be after sentence date", ValidationCode.OFFENCE_DATE_AFTER_SENTENCE_START_DATE, sentencesAndOffence.sentenceSequence)
    }
    return null
  }
}
