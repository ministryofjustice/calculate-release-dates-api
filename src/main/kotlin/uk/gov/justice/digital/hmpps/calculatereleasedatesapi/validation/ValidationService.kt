package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation

import org.springframework.stereotype.Service
import org.threeten.extra.LocalDateRange
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.FeatureToggles
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.AdjustmentIsAfterReleaseDateException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.CustodialPeriodExtinguishedException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.RemandPeriodOverlapsWithRemandException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.RemandPeriodOverlapsWithSentenceException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculableSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ExtendedDeterminateSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.StandardDeterminateSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BookingAndSentenceAdjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonApiSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonerDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAdjustmentType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAdjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAndOffences
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.ImportantDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.SentencesExtractionService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.isAfterOrEqualTo
import java.time.LocalDate

@Service
class ValidationService(
  private val featureToggles: FeatureToggles,
  private val extractionService: SentencesExtractionService
) {

  fun validate(sourceData: PrisonApiSourceData): ValidationMessages {
    val sentencesAndOffences = sourceData.sentenceAndOffences
    val adjustments = sourceData.bookingAndSentenceAdjustments
    val sortedSentences = sentencesAndOffences.sortedWith(this::sortByCaseNumberAndLineSequence)

    val validateOffender = validateOffenderSupported(sourceData.prisonerDetails)
    if (validateOffender.isNotEmpty()) {
      return ValidationMessages(ValidationType.UNSUPPORTED, validateOffender)
    }

    val unsupportedValidationMessages = validateSupportedSentences(sortedSentences)
    if (unsupportedValidationMessages.isNotEmpty()) {
      return ValidationMessages(ValidationType.UNSUPPORTED, unsupportedValidationMessages)
    }

    val validationMessages = validateSentences(sortedSentences)
    validationMessages += validateAdjustments(adjustments)
    if (validationMessages.isNotEmpty()) {
      return ValidationMessages(ValidationType.VALIDATION, validationMessages)
    }

    return ValidationMessages(ValidationType.VALID)
  }

  private fun validateSentences(sentences: List<SentenceAndOffences>): MutableList<ValidationMessage> {
    val validationMessages = sentences.map { validateSentence(it) }.flatten().toMutableList()
    validationMessages += validateConsecutiveSentenceUnique(sentences)
    return validationMessages
  }

  private fun validateConsecutiveSentenceUnique(sentences: List<SentenceAndOffences>): List<ValidationMessage> {
    val consecutiveSentences = sentences.filter { it.consecutiveToSequence != null }
    val sentencesGroupedByConsecutiveTo = consecutiveSentences.groupBy { it.consecutiveToSequence }
    return sentencesGroupedByConsecutiveTo.entries
      .filter { it.value.size > 1 }
      .map { entry ->
        ValidationMessage("Multiple sentences are consecutive to the same sentence", ValidationCode.MULTIPLE_SENTENCES_CONSECUTIVE_TO, entry.key, arguments = entry.value.map { it.sentenceSequence.toString() })
      }
  }

  private fun validateAdjustments(adjustments: BookingAndSentenceAdjustments): List<ValidationMessage> {
    val validationMessages = adjustments.sentenceAdjustments.mapNotNull { validateAdjustment(it) }.toMutableList()
    validationMessages += validateRemandOverlappingRemand(adjustments)
    return validationMessages
  }

  private fun validateRemandOverlappingRemand(adjustments: BookingAndSentenceAdjustments): List<ValidationMessage> {
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
      validateDuration(it),
      validateThatSec91SentenceTypeCorrectlyApplied(it)
    )
  }

  private fun validateThatSec91SentenceTypeCorrectlyApplied(sentencesAndOffence: SentenceAndOffences): ValidationMessage? {
    val sentenceCalculationType = SentenceCalculationType.from(sentencesAndOffence.sentenceCalculationType)!!

    if (sentenceCalculationType == SentenceCalculationType.SEC91_03 || sentenceCalculationType == SentenceCalculationType.SEC91_03_ORA) {
      if (sentencesAndOffence.sentenceDate.isAfterOrEqualTo(ImportantDates.SEC_91_END_DATE)) {
        return ValidationMessage("${sentencesAndOffence.sentenceCalculationType} sentence type incorrectly applied", ValidationCode.SEC_91_SENTENCE_TYPE_INCORRECT, sentencesAndOffence.sentenceSequence, listOf(sentencesAndOffence.sentenceCalculationType))
      }
    }
    return null
  }

  private fun validateDuration(sentencesAndOffence: SentenceAndOffences): ValidationMessage? {
    val hasMultipleTerms = sentencesAndOffence.terms.size > 1
    val sentenceCalculationType = SentenceCalculationType.from(sentencesAndOffence.sentenceCalculationType)!!
    if (sentenceCalculationType.sentenceClazz == StandardDeterminateSentence::class.java && hasMultipleTerms) {
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

  private fun validateOffenderSupported(prisonerDetails: PrisonerDetails): List<ValidationMessage> {
    val hasPtdAlert = prisonerDetails.activeAlerts().any() {
      it.alertCode == "PTD" &&
        it.alertType == "O"
    }

    if (hasPtdAlert) {
      return listOf(ValidationMessage("Prisoner has PTD alert after PCSC commencement date, this is unsupported", ValidationCode.PRISONER_SUBJECT_TO_PTD))
    }
    return emptyList()
  }
  private fun validateSupportedSentences(sentencesAndOffences: List<SentenceAndOffences>): List<ValidationMessage> {
    val supportedSentences: List<SentenceCalculationType> = SentenceCalculationType.values()
      .filter { (featureToggles.eds && it.sentenceClazz == ExtendedDeterminateSentence::class.java) || it.sentenceClazz == StandardDeterminateSentence::class.java }
    var sds = false
    var eds = false
    val validationMessages = sentencesAndOffences.filter {
      val sentenceType = SentenceCalculationType.from(it.sentenceCalculationType)
      if (sentenceType != null) {
        sds = sds || sentenceType.sentenceClazz == StandardDeterminateSentence::class.java
        eds = eds || sentenceType.sentenceClazz == ExtendedDeterminateSentence::class.java
      }
      !supportedSentences.contains(sentenceType)
    }
      .map { ValidationMessage("Unsupported sentence type ${it.sentenceTypeDescription}", ValidationCode.UNSUPPORTED_SENTENCE_TYPE, it.sentenceSequence, listOf(it.sentenceTypeDescription)) }.toMutableList()
    if (sds && eds) {
      validationMessages.add(ValidationMessage("Booking contains SDS and EDS sentences, this is currently not supported", ValidationCode.UNSUPPORTED_SENTENCE_TYPE, -1, listOf("SDS and EDS sentences")))
    }
    return validationMessages.toList()
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

  /*
    Run the validation that can only happen after calculations. I.e. validate that adjustments happen before release date
   */
  fun validateAfterCalculation(workingBooking: Booking) {
    workingBooking.sentenceGroups.forEach { validateSentenceHasNotBeenExtinguished(it) }
    validateRemandOverlappingSentences(workingBooking)
    validateAdditionAdjustmentsInsideLatestReleaseDate(workingBooking)
  }

  private fun validateAdditionAdjustmentsInsideLatestReleaseDate(booking: Booking) {
    val sentences = booking.getAllExtractableSentences()
    val latestReleaseDatePreAddedDays = sentences.maxOf { it.sentenceCalculation.releaseDateWithoutAdditions }

    val adas = booking.adjustments.getOrEmptyList(AdjustmentType.ADDITIONAL_DAYS_AWARDED)
    val radas = booking.adjustments.getOrEmptyList(AdjustmentType.RESTORATION_OF_ADDITIONAL_DAYS_AWARDED)
    val uals = booking.adjustments.getOrEmptyList(AdjustmentType.UNLAWFULLY_AT_LARGE)
    val adjustments = adas + radas + uals

    val adjustmentsAfterRelease = adjustments.filter {
      it.appliesToSentencesFrom.isAfter(latestReleaseDatePreAddedDays)
    }
    if (adjustmentsAfterRelease.isNotEmpty()) {
      var anyAda = false
      var anyRada = false
      var anyUal = false
      adjustmentsAfterRelease.forEach {
        anyAda = anyAda || adas.contains(it)
        anyRada = anyRada || radas.contains(it)
        anyUal = anyUal || uals.contains(it)
      }
      val arguments = mutableListOf<String>()
      if (anyAda)
        arguments.add(AdjustmentType.ADDITIONAL_DAYS_AWARDED.name)
      if (anyRada)
        arguments.add(AdjustmentType.RESTORATION_OF_ADDITIONAL_DAYS_AWARDED.name)
      if (anyUal)
        arguments.add(AdjustmentType.UNLAWFULLY_AT_LARGE.name)
      throw AdjustmentIsAfterReleaseDateException(
        "Adjustments are applied after latest release date of booking",
        arguments
      )
    }
  }

  private fun validateRemandOverlappingSentences(booking: Booking) {
    val remandPeriods = booking.adjustments.getOrEmptyList(AdjustmentType.REMAND)
    if (remandPeriods.isNotEmpty()) {
      val remandRanges = remandPeriods.map { LocalDateRange.of(it.fromDate, it.toDate) }
      val sentenceRanges = booking.getAllExtractableSentences().map { LocalDateRange.of(it.sentencedAt, it.sentenceCalculation.adjustedDeterminateReleaseDate) }

      val allRanges = (remandRanges + sentenceRanges).sortedBy { it.start }
      var totalRange: LocalDateRange? = null
      var previousRangeIsRemand: Boolean? = null
      var previousRange: LocalDateRange? = null

      allRanges.forEach {
        val isRemand = remandRanges.any { sentenceRange -> sentenceRange === it }
        if (totalRange == null && previousRangeIsRemand == null) {
          totalRange = it
        } else if (it.isConnected(totalRange) &&
          (previousRangeIsRemand!! || isRemand)
        ) {
          // Remand overlaps
          if (previousRangeIsRemand!! && isRemand) {
            throw RemandPeriodOverlapsWithRemandException("Remand of range ${previousRange!!} overlaps with remand of range $it")
          } else {
            throw RemandPeriodOverlapsWithSentenceException("${if (previousRangeIsRemand!!) "Remand" else "Sentence"} of range ${previousRange!!} overlaps with ${if (isRemand) "remand" else "sentence"} of range $it")
          }
        } else if (it.end.isAfter(totalRange!!.end)) {
          totalRange = LocalDateRange.of(totalRange!!.start, it.end)
        }
        previousRangeIsRemand = isRemand
        previousRange = it
      }
    }
  }

  private fun validateSentenceHasNotBeenExtinguished(sentences: List<CalculableSentence>) {
    val determinateSentences = sentences.filter { !it.isRecall() }
    if (determinateSentences.isNotEmpty()) {
      val earliestSentenceDate = determinateSentences.minOf { it.sentencedAt }
      val latestReleaseDateSentence = extractionService.mostRecentSentence(
        determinateSentences, SentenceCalculation::adjustedUncappedDeterminateReleaseDate
      )
      if (earliestSentenceDate.minusDays(1).isAfter(latestReleaseDateSentence.sentenceCalculation.adjustedUncappedDeterminateReleaseDate)) {
        val hasRemand = latestReleaseDateSentence.sentenceCalculation.getAdjustmentBeforeSentence(AdjustmentType.REMAND) != 0
        val hasTaggedBail = latestReleaseDateSentence.sentenceCalculation.getAdjustmentBeforeSentence(AdjustmentType.TAGGED_BAIL) != 0
        val arguments: MutableList<String> = mutableListOf()
        if (hasRemand) {
          arguments += AdjustmentType.REMAND.name
        }
        if (hasTaggedBail) {
          arguments += AdjustmentType.TAGGED_BAIL.name
        }
        throw CustodialPeriodExtinguishedException("Custodial period extinguished", arguments)
      }
    }
  }
}
