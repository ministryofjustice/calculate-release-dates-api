package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation

import org.springframework.stereotype.Service
import org.threeten.extra.LocalDateRange
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.AdjustmentIsAfterReleaseDateException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.CustodialPeriodExtinguishedException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.RemandPeriodOverlapsWithRemandException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.RemandPeriodOverlapsWithSentenceException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculableSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ExtendedDeterminateSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SopcSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.StandardDeterminateSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BookingAdjustmentType.ADDITIONAL_DAYS_AWARDED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BookingAdjustmentType.RESTORED_ADDITIONAL_DAYS_AWARDED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BookingAdjustmentType.UNLAWFULLY_AT_LARGE
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BookingAdjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BookingAndSentenceAdjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonApiSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonerDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAdjustmentType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAdjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAndOffences
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceTerms
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.ImportantDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.SentencesExtractionService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.transform
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.isAfterOrEqualTo
import java.time.LocalDate
import java.time.Period

@Service
class ValidationService(
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
    val validationMessages = adjustments.sentenceAdjustments.mapNotNull { validateSentenceAdjustment(it) }.toMutableList()
    validationMessages += listOfNotNull(validateBookingAdjustment(adjustments.bookingAdjustments))
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

  private fun validateBookingAdjustment(bookingAdjustments: List<BookingAdjustments>): ValidationMessage? {
    val invalidAdjustmentTypes = bookingAdjustments.filter {
      BOOKING_ADJUSTMENTS_TO_VALIDATE.contains(it.type) && it.fromDate.isAfter(LocalDate.now())
    }.map { it.type }.distinct()
    if (invalidAdjustmentTypes.isNotEmpty()) {
      return ValidationMessage("Adjustment should not be future dated.", ValidationCode.ADJUSTMENT_FUTURE_DATED, arguments = invalidAdjustmentTypes.map { transform(it)!!.name })
    }
    return null
  }

  private fun validateSentenceAdjustment(sentenceAdjustment: SentenceAdjustments): ValidationMessage? {
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
    ) + validateDuration(it) + listOfNotNull(validateThatSec91SentenceTypeCorrectlyApplied(it), validateEdsSentenceTypesCorrectlyApplied(it), validateSopcSentenceTypesCorrectlyApplied(it))
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

  private fun validateEdsSentenceTypesCorrectlyApplied(sentencesAndOffence: SentenceAndOffences): ValidationMessage? {
    val sentenceCalculationType = SentenceCalculationType.from(sentencesAndOffence.sentenceCalculationType)!!

    if (listOf(SentenceCalculationType.EDS18, SentenceCalculationType.EDS21, SentenceCalculationType.EDSU18).contains(sentenceCalculationType)) {
      if (sentencesAndOffence.sentenceDate.isBefore(ImportantDates.EDS18_SENTENCE_TYPES_START_DATE)) {
        return ValidationMessage("${sentencesAndOffence.sentenceCalculationType} sentence type incorrectly applied", ValidationCode.EDS18_EDS21_EDSU18_SENTENCE_TYPE_INCORRECT, sentencesAndOffence.sentenceSequence, listOf(sentencesAndOffence.sentenceCalculationType))
      }
    } else if (sentenceCalculationType == SentenceCalculationType.LASPO_AR) {
      if (sentencesAndOffence.sentenceDate.isAfterOrEqualTo(ImportantDates.LASPO_AR_SENTENCE_TYPES_END_DATE)) {
        return ValidationMessage("${sentencesAndOffence.sentenceCalculationType} sentence type incorrectly applied", ValidationCode.LASPO_AR_SENTENCE_TYPE_INCORRECT, sentencesAndOffence.sentenceSequence, listOf(sentencesAndOffence.sentenceCalculationType))
      }
    }
    return null
  }

  private fun validateSopcSentenceTypesCorrectlyApplied(sentencesAndOffence: SentenceAndOffences): ValidationMessage? {
    val sentenceCalculationType = SentenceCalculationType.from(sentencesAndOffence.sentenceCalculationType)!!

    if (listOf(SentenceCalculationType.SOPC18, SentenceCalculationType.SOPC21).contains(sentenceCalculationType)) {
      if (sentencesAndOffence.sentenceDate.isBefore(ImportantDates.SEC_91_END_DATE)) {
        return ValidationMessage("${sentencesAndOffence.sentenceCalculationType} sentence type incorrectly applied", ValidationCode.SOPC18_SOPC21_SENTENCE_TYPE_INCORRECT, sentencesAndOffence.sentenceSequence, listOf(sentencesAndOffence.sentenceCalculationType))
      }
    } else if (sentenceCalculationType == SentenceCalculationType.SEC236A) {
      if (sentencesAndOffence.sentenceDate.isAfterOrEqualTo(ImportantDates.SEC_91_END_DATE)) {
        return ValidationMessage("${sentencesAndOffence.sentenceCalculationType} sentence type incorrectly applied", ValidationCode.SEC236A_SENTENCE_TYPE_INCORRECT, sentencesAndOffence.sentenceSequence, listOf(sentencesAndOffence.sentenceCalculationType))
      }
    }
    return null
  }
  private fun validateDuration(sentencesAndOffence: SentenceAndOffences): List<ValidationMessage> {
    val sentenceCalculationType = SentenceCalculationType.from(sentencesAndOffence.sentenceCalculationType)!!
    if (sentenceCalculationType.sentenceClazz == StandardDeterminateSentence::class.java) {
      return validateSingleTermDuration(sentencesAndOffence)
    } else {
      return validateImprisonmentAndLicenceTermDuration(sentencesAndOffence)
    }
  }

  private fun validateSingleTermDuration(sentencesAndOffence: SentenceAndOffences): List<ValidationMessage> {
    val validationMessages = mutableListOf<ValidationMessage>()
    val hasMultipleTerms = sentencesAndOffence.terms.size > 1
    if (hasMultipleTerms) {
      validationMessages.add(ValidationMessage("Sentence has multiple terms", ValidationCode.SENTENCE_HAS_MULTIPLE_TERMS, sentencesAndOffence.sentenceSequence))
    } else {
      val emptyImprisonmentTerm =
        sentencesAndOffence.terms[0].days == 0 &&
          sentencesAndOffence.terms[0].weeks == 0 &&
          sentencesAndOffence.terms[0].months == 0 &&
          sentencesAndOffence.terms[0].years == 0

      if (emptyImprisonmentTerm) {
        validationMessages.add(ValidationMessage("Sentence empty imprisonment term", ValidationCode.ZERO_IMPRISONMENT_TERM, sentencesAndOffence.sentenceSequence))
      }
    }
    return validationMessages
  }

  private fun validateImprisonmentAndLicenceTermDuration(sentencesAndOffence: SentenceAndOffences): List<ValidationMessage> {
    val validationMessages = mutableListOf<ValidationMessage>()

    val imprisonmentTerms = sentencesAndOffence.terms.filter { it.code == SentenceTerms.IMPRISONMENT_TERM_CODE }
    if (imprisonmentTerms.isEmpty()) {
      validationMessages.add(
        ValidationMessage(
          "Sentence has no imprisonment term",
          ValidationCode.SENTENCE_HAS_NO_IMPRISONMENT_TERM,
          sentencesAndOffence.sentenceSequence
        )
      )
    } else if (imprisonmentTerms.size > 1) {
      validationMessages.add(ValidationMessage("Sentence has multiple imprisonment terms", ValidationCode.MORE_THAN_ONE_IMPRISONMENT_TERM, sentencesAndOffence.sentenceSequence))
    } else {
      val emptyTerm = imprisonmentTerms[0].days == 0 &&
        imprisonmentTerms[0].weeks == 0 &&
        imprisonmentTerms[0].months == 0 &&
        imprisonmentTerms[0].years == 0
      if (emptyTerm) {
        validationMessages.add(ValidationMessage("Sentence empty imprisonment term", ValidationCode.ZERO_IMPRISONMENT_TERM, sentencesAndOffence.sentenceSequence))
      }
    }

    val licenceTerms = sentencesAndOffence.terms.filter { it.code == SentenceTerms.LICENCE_TERM_CODE }
    if (licenceTerms.isEmpty()) {
      validationMessages.add(ValidationMessage("Sentence has no licence term", ValidationCode.SENTENCE_HAS_NO_LICENCE_TERM, sentencesAndOffence.sentenceSequence))
    } else if (licenceTerms.size > 1) {
      validationMessages.add(ValidationMessage("Sentence has multiple licence terms", ValidationCode.MORE_THAN_ONE_LICENCE_TERM, sentencesAndOffence.sentenceSequence))
    } else {
      val sentenceCalculationType = SentenceCalculationType.from(sentencesAndOffence.sentenceCalculationType)!!
      if (sentenceCalculationType.sentenceClazz == ExtendedDeterminateSentence::class.java) {
        val duration =
          Period.of(licenceTerms[0].years, licenceTerms[0].months, licenceTerms[0].weeks * 7 + licenceTerms[0].days)
        val endOfDuration = sentencesAndOffence.sentenceDate.plus(duration)
        val endOfOneYear = sentencesAndOffence.sentenceDate.plusYears(1)
        val endOfEightYears = sentencesAndOffence.sentenceDate.plusYears(8)

        if (endOfDuration.isBefore(endOfOneYear)) {
          validationMessages.add(
            ValidationMessage(
              "EDS Licence duration less than one year",
              ValidationCode.EDS_LICENCE_TERM_LESS_THAN_ONE_YEAR,
              sentencesAndOffence.sentenceSequence
            )
          )
        } else if (endOfDuration.isAfter(endOfEightYears)) {
          validationMessages.add(
            ValidationMessage(
              "EDS Licence duration greater than eight years",
              ValidationCode.EDS_LICENCE_TERM_MORE_THAN_EIGHT_YEARS,
              sentencesAndOffence.sentenceSequence
            )
          )
        }
      } else if (sentenceCalculationType.sentenceClazz == SopcSentence::class.java) {
        val duration =
          Period.of(licenceTerms[0].years, licenceTerms[0].months, licenceTerms[0].weeks * 7 + licenceTerms[0].days)
        val endOfDuration = sentencesAndOffence.sentenceDate.plus(duration)
        val endOfOneYear = sentencesAndOffence.sentenceDate.plusYears(1)
        if (endOfDuration != endOfOneYear) {
          validationMessages.add(
            ValidationMessage(
              " SOPC Licence duration should be exactly 1 year/12 months",
              ValidationCode.SOPC_LICENCE_TERM_NOT_12_MONTHS,
              sentencesAndOffence.sentenceSequence
            )
          )
        }
      }
    }

    return validationMessages
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
      .filter { it.sentenceClazz == SopcSentence::class.java || it.sentenceClazz == ExtendedDeterminateSentence::class.java || it.sentenceClazz == StandardDeterminateSentence::class.java }
    val validationMessages = sentencesAndOffences.filter {
      !supportedSentences.contains(SentenceCalculationType.from(it.sentenceCalculationType))
    }
      .map { ValidationMessage("Unsupported sentence type ${it.sentenceTypeDescription}", ValidationCode.UNSUPPORTED_SENTENCE_TYPE, it.sentenceSequence, listOf(it.sentenceTypeDescription)) }.toMutableList()
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

  companion object {
    private val BOOKING_ADJUSTMENTS_TO_VALIDATE = listOf(ADDITIONAL_DAYS_AWARDED, UNLAWFULLY_AT_LARGE, RESTORED_ADDITIONAL_DAYS_AWARDED)
  }
}
