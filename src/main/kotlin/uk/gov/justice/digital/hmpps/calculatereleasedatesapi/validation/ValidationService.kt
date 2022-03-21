package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation

import org.springframework.stereotype.Service
import org.threeten.extra.LocalDateRange
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.FeatureToggles
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BookingAndSentenceAdjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonApiSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAdjustmentType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAdjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAndOffences
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType

@Service
class ValidationService(
  private val featureToggles: FeatureToggles
) {

  fun validate(sourceData: PrisonApiSourceData): ValidationMessages {
    val sentencesAndOffences = sourceData.sentenceAndOffences
    val adjustments = sourceData.bookingAndSentenceAdjustments
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
    val validationMessages = adjustments.sentenceAdjustments.mapNotNull { validateAdjustment(it) }.toMutableList()
    validationMessages += validateRemandOverlapping(adjustments)
    return validationMessages
  }

  private fun validateRemandOverlapping(adjustments: BookingAndSentenceAdjustments): List<ValidationMessage> {
    val remandPeriods = adjustments.sentenceAdjustments.filter { it.type == SentenceAdjustmentType.REMAND && it.fromDate != null && it.toDate != null }
    if (remandPeriods.isNotEmpty()) {
      val remandRanges = remandPeriods.map { LocalDateRange.of(it.fromDate, it.toDate) }

      var totalRange: LocalDateRange? = null

      remandRanges.forEach {
        if (totalRange == null) {
          totalRange = it
        } else if (it.isConnected(totalRange)) {
          return listOf(ValidationMessage("Remand periods are overlapping", ValidationCode.REMAND_OVERLAPS_WITH_REMAND))
        }
      }
    }
    return emptyList()
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
      validateDuration(it)
    )
  }

  private fun validateDuration(sentencesAndOffence: SentenceAndOffences): ValidationMessage? {
    val hasMultipleTerms = sentencesAndOffence.terms.size > 1
    if (hasMultipleTerms) {
      return ValidationMessage("Sentence has multiple terms", ValidationCode.SENTENCE_HAS_MULTIPLE_TERMS, sentencesAndOffence.sentenceSequence)
    }
    val invalid = sentencesAndOffence.terms.isEmpty() ||
      (
        sentencesAndOffence.terms[0].days == 0 &&
          sentencesAndOffence.terms[0].weeks == 0 &&
          sentencesAndOffence.terms[0].months == 0 &&
          sentencesAndOffence.terms[0].years == 0
        )
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
    val supportedSentences: List<SentenceCalculationType> = SentenceCalculationType.values()
      .filter { (featureToggles.recall && it.sentenceType.isRecall) || it.sentenceType == SentenceType.STANDARD_DETERMINATE }
    return sentencesAndOffences.filter { !supportedSentences.contains(SentenceCalculationType.from(it.sentenceCalculationType)) }
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
