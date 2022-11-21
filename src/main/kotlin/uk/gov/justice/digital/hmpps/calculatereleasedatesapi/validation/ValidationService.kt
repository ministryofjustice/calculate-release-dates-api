package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.threeten.extra.LocalDateRange
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.FeatureToggles
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.AdjustmentIsAfterReleaseDateException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.CustodialPeriodExtinguishedException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.RemandPeriodOverlapsWithRemandException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.RemandPeriodOverlapsWithSentenceException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AFineSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculableSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ExtendedDeterminateSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SopcSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.StandardDeterminateSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BookingAdjustment
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BookingAdjustmentType.ADDITIONAL_DAYS_AWARDED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BookingAdjustmentType.LAWFULLY_AT_LARGE
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BookingAdjustmentType.RESTORED_ADDITIONAL_DAYS_AWARDED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BookingAdjustmentType.SPECIAL_REMISSION
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BookingAdjustmentType.UNLAWFULLY_AT_LARGE
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BookingAndSentenceAdjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonApiSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonerDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAdjustment
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAdjustmentType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAndOffences
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceTerms
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.ImportantDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.SentencesExtractionService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.isAfterOrEqualTo
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.ADJUSTMENT_AFTER_RELEASE_ADA
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.ADJUSTMENT_AFTER_RELEASE_RADA
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.ADJUSTMENT_AFTER_RELEASE_UAL
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.ADJUSTMENT_FUTURE_DATED_ADA
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.ADJUSTMENT_FUTURE_DATED_RADA
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.ADJUSTMENT_FUTURE_DATED_UAL
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.A_FINE_SENTENCE_CONSECUTIVE
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.A_FINE_SENTENCE_CONSECUTIVE_TO
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.A_FINE_SENTENCE_MISSING_FINE_AMOUNT
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.A_FINE_SENTENCE_WITH_PAYMENTS
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.CUSTODIAL_PERIOD_EXTINGUISHED_REMAND
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.CUSTODIAL_PERIOD_EXTINGUISHED_TAGGED_BAIL
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.EDS18_EDS21_EDSU18_SENTENCE_TYPE_INCORRECT
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.EDS_LICENCE_TERM_LESS_THAN_ONE_YEAR
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.EDS_LICENCE_TERM_MORE_THAN_EIGHT_YEARS
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.LASPO_AR_SENTENCE_TYPE_INCORRECT
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.MORE_THAN_ONE_IMPRISONMENT_TERM
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.MORE_THAN_ONE_LICENCE_TERM
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.MULTIPLE_SENTENCES_CONSECUTIVE_TO
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.OFFENCE_DATE_AFTER_SENTENCE_RANGE_DATE
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.OFFENCE_DATE_AFTER_SENTENCE_START_DATE
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.OFFENCE_MISSING_DATE
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.PRISONER_SUBJECT_TO_PTD
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.REMAND_FROM_TO_DATES_REQUIRED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.REMAND_OVERLAPS_WITH_REMAND
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.REMAND_OVERLAPS_WITH_SENTENCE
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.SEC236A_SENTENCE_TYPE_INCORRECT
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.SEC_91_SENTENCE_TYPE_INCORRECT
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.SENTENCE_HAS_MULTIPLE_TERMS
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.SENTENCE_HAS_NO_IMPRISONMENT_TERM
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.SENTENCE_HAS_NO_LICENCE_TERM
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.SOPC18_SOPC21_SENTENCE_TYPE_INCORRECT
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.SOPC_LICENCE_TERM_NOT_12_MONTHS
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.UNSUPPORTED_ADJUSTMENT_LAWFULLY_AT_LARGE
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.UNSUPPORTED_ADJUSTMENT_SPECIAL_REMISSION
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.UNSUPPORTED_SENTENCE_TYPE
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.ZERO_IMPRISONMENT_TERM
import java.time.LocalDate
import java.time.Period

@Service
class ValidationService(
  private val featureToggles: FeatureToggles,
  private val extractionService: SentencesExtractionService
) {

  fun validate(sourceData: PrisonApiSourceData): List<ValidationMessage> {
    val sentencesAndOffences = sourceData.sentenceAndOffences
    val adjustments = sourceData.bookingAndSentenceAdjustments
    val sortedSentences = sentencesAndOffences.sortedWith(this::sortByCaseNumberAndLineSequence)

    val validateOffender = validateOffenderSupported(sourceData.prisonerDetails)
    if (validateOffender.isNotEmpty()) {
      return validateOffender
    }

    val unsupportedValidationMessages = validateSupportedSentences(sortedSentences)
    if (unsupportedValidationMessages.isNotEmpty()) {
      return unsupportedValidationMessages
    }

    val unsupportedCalculationMessages = validateUnsupportedCalculation(sourceData)
    if (unsupportedCalculationMessages.isNotEmpty()) {
      return unsupportedCalculationMessages
    }

    val validationMessages = validateSentences(sortedSentences)
    validationMessages += validateAdjustments(adjustments)
    if (validationMessages.isNotEmpty()) {
      return validationMessages
    }

    return emptyList()
  }

  private fun validateUnsupportedCalculation(sourceData: PrisonApiSourceData): List<ValidationMessage> {
    val messages = validateFineSentenceSupported(sourceData).toMutableList()
    messages += validateSupportedAdjustments(sourceData.bookingAndSentenceAdjustments.bookingAdjustments)
    return messages
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
        val consecutiveToSentence = sentences.first { it.sentenceSequence == entry.key }
        ValidationMessage(MULTIPLE_SENTENCES_CONSECUTIVE_TO, getCaseSeqAndLineSeq(consecutiveToSentence))
      }
  }

  private fun validateAdjustments(adjustments: BookingAndSentenceAdjustments): List<ValidationMessage> {
    val validationMessages =
      adjustments.sentenceAdjustments.mapNotNull { validateSentenceAdjustment(it) }.toMutableList()
    validationMessages.addAll(validateBookingAdjustment(adjustments.bookingAdjustments))
    validationMessages += validateRemandOverlappingRemand(adjustments)
    return validationMessages
  }

  private fun validateSupportedAdjustments(adjustments: List<BookingAdjustment>): List<ValidationMessage> {
    val messages = mutableListOf<ValidationMessage>()
    if (adjustments.any { it.type == LAWFULLY_AT_LARGE }) messages.add(
      ValidationMessage(
        UNSUPPORTED_ADJUSTMENT_LAWFULLY_AT_LARGE
      )
    )
    if (adjustments.any { it.type == SPECIAL_REMISSION }) messages.add(
      ValidationMessage(
        UNSUPPORTED_ADJUSTMENT_SPECIAL_REMISSION
      )
    )
    return messages
  }

  private fun validateRemandOverlappingRemand(adjustments: BookingAndSentenceAdjustments): List<ValidationMessage> {
    val remandPeriods =
      adjustments.sentenceAdjustments.filter { it.type == SentenceAdjustmentType.REMAND && it.fromDate != null && it.toDate != null }
    if (remandPeriods.isNotEmpty()) {
      val remandRanges = remandPeriods.map { LocalDateRange.of(it.fromDate, it.toDate) }

      var totalRange: LocalDateRange? = null

      remandRanges.forEach {
        if (totalRange == null) {
          totalRange = it
        } else if (it.isConnected(totalRange)) {
          return listOf(ValidationMessage(REMAND_OVERLAPS_WITH_REMAND))
        }
      }
    }
    return emptyList()
  }

  private fun validateBookingAdjustment(bookingAdjustments: List<BookingAdjustment>): List<ValidationMessage> =
    bookingAdjustments
      .filter { BOOKING_ADJUSTMENTS_TO_VALIDATE.contains(it.type) && it.fromDate.isAfter(LocalDate.now()) }
      .map { it.type }
      .distinct()
      .map { ValidationMessage(ADJUSTMENT_FUTURE_DATED_MAP[it]!!) }

  private fun validateSentenceAdjustment(sentenceAdjustment: SentenceAdjustment): ValidationMessage? {
    if (sentenceAdjustment.type == SentenceAdjustmentType.REMAND && (sentenceAdjustment.fromDate == null || sentenceAdjustment.toDate == null)) {
      return ValidationMessage(REMAND_FROM_TO_DATES_REQUIRED)
    }
    return null
  }

  private fun validateSentence(it: SentenceAndOffences): List<ValidationMessage> {
    return listOfNotNull(
      validateWithoutOffenceDate(it),
      validateOffenceDateAfterSentenceDate(it),
      validateOffenceRangeDateAfterSentenceDate(it),
    ) + validateDuration(it) + listOfNotNull(
      validateThatSec91SentenceTypeCorrectlyApplied(it),
      validateEdsSentenceTypesCorrectlyApplied(it),
      validateSopcSentenceTypesCorrectlyApplied(it),
      validateFineAmount(it)
    )
  }

  private fun validateFineAmount(sentencesAndOffence: SentenceAndOffences): ValidationMessage? {
    val sentenceCalculationType = SentenceCalculationType.from(sentencesAndOffence.sentenceCalculationType)!!
    if (sentenceCalculationType.sentenceClazz == AFineSentence::class.java) {
      if (sentencesAndOffence.fineAmount == null) {
        return ValidationMessage(A_FINE_SENTENCE_MISSING_FINE_AMOUNT, getCaseSeqAndLineSeq(sentencesAndOffence))
      }
    }
    return null
  }

  private fun validateThatSec91SentenceTypeCorrectlyApplied(sentencesAndOffence: SentenceAndOffences): ValidationMessage? {
    val sentenceCalculationType = SentenceCalculationType.from(sentencesAndOffence.sentenceCalculationType)!!

    if (sentenceCalculationType == SentenceCalculationType.SEC91_03 || sentenceCalculationType == SentenceCalculationType.SEC91_03_ORA) {
      if (sentencesAndOffence.sentenceDate.isAfterOrEqualTo(ImportantDates.SEC_91_END_DATE)) {
        return ValidationMessage(SEC_91_SENTENCE_TYPE_INCORRECT, getCaseSeqAndLineSeq(sentencesAndOffence))
      }
    }
    return null
  }

  private fun validateEdsSentenceTypesCorrectlyApplied(sentencesAndOffence: SentenceAndOffences): ValidationMessage? {
    val sentenceCalculationType = SentenceCalculationType.from(sentencesAndOffence.sentenceCalculationType)!!

    if (listOf(SentenceCalculationType.EDS18, SentenceCalculationType.EDS21, SentenceCalculationType.EDSU18).contains(
        sentenceCalculationType
      )
    ) {
      if (sentencesAndOffence.sentenceDate.isBefore(ImportantDates.EDS18_SENTENCE_TYPES_START_DATE)) {
        return ValidationMessage(EDS18_EDS21_EDSU18_SENTENCE_TYPE_INCORRECT, getCaseSeqAndLineSeq(sentencesAndOffence))
      }
    } else if (sentenceCalculationType == SentenceCalculationType.LASPO_AR) {
      if (sentencesAndOffence.sentenceDate.isAfterOrEqualTo(ImportantDates.LASPO_AR_SENTENCE_TYPES_END_DATE)) {
        return ValidationMessage(LASPO_AR_SENTENCE_TYPE_INCORRECT, getCaseSeqAndLineSeq(sentencesAndOffence))
      }
    }
    return null
  }

  private fun validateSopcSentenceTypesCorrectlyApplied(sentencesAndOffence: SentenceAndOffences): ValidationMessage? {
    val sentenceCalculationType = SentenceCalculationType.from(sentencesAndOffence.sentenceCalculationType)!!

    if (listOf(SentenceCalculationType.SOPC18, SentenceCalculationType.SOPC21).contains(sentenceCalculationType)) {
      if (sentencesAndOffence.sentenceDate.isBefore(ImportantDates.SEC_91_END_DATE)) {
        return ValidationMessage(SOPC18_SOPC21_SENTENCE_TYPE_INCORRECT, getCaseSeqAndLineSeq(sentencesAndOffence))
      }
    } else if (sentenceCalculationType == SentenceCalculationType.SEC236A) {
      if (sentencesAndOffence.sentenceDate.isAfterOrEqualTo(ImportantDates.SEC_91_END_DATE)) {
        return ValidationMessage(SEC236A_SENTENCE_TYPE_INCORRECT, getCaseSeqAndLineSeq(sentencesAndOffence))
      }
    }
    return null
  }

  private fun validateDuration(sentencesAndOffence: SentenceAndOffences): List<ValidationMessage> {
    val sentenceCalculationType = SentenceCalculationType.from(sentencesAndOffence.sentenceCalculationType)!!
    return if (sentenceCalculationType.sentenceClazz == StandardDeterminateSentence::class.java || sentenceCalculationType.sentenceClazz == AFineSentence::class.java) {
      validateSingleTermDuration(sentencesAndOffence)
    } else {
      validateImprisonmentAndLicenceTermDuration(sentencesAndOffence)
    }
  }

  private fun validateSingleTermDuration(sentencesAndOffence: SentenceAndOffences): List<ValidationMessage> {
    val validationMessages = mutableListOf<ValidationMessage>()
    val hasMultipleTerms = sentencesAndOffence.terms.size > 1
    if (hasMultipleTerms) {
      validationMessages.add(ValidationMessage(SENTENCE_HAS_MULTIPLE_TERMS, getCaseSeqAndLineSeq(sentencesAndOffence)))
    } else {
      val emptyImprisonmentTerm =
        sentencesAndOffence.terms[0].days == 0 &&
          sentencesAndOffence.terms[0].weeks == 0 &&
          sentencesAndOffence.terms[0].months == 0 &&
          sentencesAndOffence.terms[0].years == 0

      if (emptyImprisonmentTerm) {
        validationMessages.add(ValidationMessage(ZERO_IMPRISONMENT_TERM, getCaseSeqAndLineSeq(sentencesAndOffence)))
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
          SENTENCE_HAS_NO_IMPRISONMENT_TERM,
          getCaseSeqAndLineSeq(sentencesAndOffence)
        )
      )
    } else if (imprisonmentTerms.size > 1) {
      validationMessages.add(
        ValidationMessage(
          MORE_THAN_ONE_IMPRISONMENT_TERM,
          getCaseSeqAndLineSeq(sentencesAndOffence)
        )
      )
    } else {
      val emptyTerm = imprisonmentTerms[0].days == 0 &&
        imprisonmentTerms[0].weeks == 0 &&
        imprisonmentTerms[0].months == 0 &&
        imprisonmentTerms[0].years == 0
      if (emptyTerm) {
        validationMessages.add(ValidationMessage(ZERO_IMPRISONMENT_TERM, getCaseSeqAndLineSeq(sentencesAndOffence)))
      }
    }

    val licenceTerms = sentencesAndOffence.terms.filter { it.code == SentenceTerms.LICENCE_TERM_CODE }
    if (licenceTerms.isEmpty()) {
      validationMessages.add(ValidationMessage(SENTENCE_HAS_NO_LICENCE_TERM, getCaseSeqAndLineSeq(sentencesAndOffence)))
    } else if (licenceTerms.size > 1) {
      validationMessages.add(ValidationMessage(MORE_THAN_ONE_LICENCE_TERM, getCaseSeqAndLineSeq(sentencesAndOffence)))
    } else {
      val sentenceCalculationType = SentenceCalculationType.from(sentencesAndOffence.sentenceCalculationType)!!
      if (sentenceCalculationType.sentenceClazz == ExtendedDeterminateSentence::class.java) {
        val duration =
          Period.of(licenceTerms[0].years, licenceTerms[0].months, licenceTerms[0].weeks * 7 + licenceTerms[0].days)
        val endOfDuration = sentencesAndOffence.sentenceDate.plus(duration)
        val endOfOneYear = sentencesAndOffence.sentenceDate.plusYears(1)
        val endOfEightYears = sentencesAndOffence.sentenceDate.plusYears(8)

        if (endOfDuration.isBefore(endOfOneYear)) {
          validationMessages.add(ValidationMessage(EDS_LICENCE_TERM_LESS_THAN_ONE_YEAR, getCaseSeqAndLineSeq(sentencesAndOffence)))
        } else if (endOfDuration.isAfter(endOfEightYears)) {
          validationMessages.add(
            ValidationMessage(
              EDS_LICENCE_TERM_MORE_THAN_EIGHT_YEARS,
              getCaseSeqAndLineSeq(sentencesAndOffence)
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
              SOPC_LICENCE_TERM_NOT_12_MONTHS,
              getCaseSeqAndLineSeq(sentencesAndOffence)
            )
          )
        }
      }
    }

    return validationMessages
  }

  private fun validateOffenceRangeDateAfterSentenceDate(sentencesAndOffence: SentenceAndOffences): ValidationMessage? {
    val invalid =
      sentencesAndOffence.offences.any { it.offenceEndDate != null && it.offenceEndDate > sentencesAndOffence.sentenceDate }
    if (invalid) {
      return ValidationMessage(OFFENCE_DATE_AFTER_SENTENCE_RANGE_DATE, getCaseSeqAndLineSeq(sentencesAndOffence))
    }
    return null
  }

  private fun validateWithoutOffenceDate(sentencesAndOffence: SentenceAndOffences): ValidationMessage? {
    val invalid = sentencesAndOffence.offences.any { it.offenceEndDate == null && it.offenceStartDate == null }
    if (invalid) {
      return ValidationMessage(OFFENCE_MISSING_DATE, getCaseSeqAndLineSeq(sentencesAndOffence))
    }
    return null
  }

  private fun sortByCaseNumberAndLineSequence(a: SentenceAndOffences, b: SentenceAndOffences): Int {
    if (a.caseSequence > b.caseSequence) return 1
    if (a.caseSequence < b.caseSequence) return -1
    return a.lineSequence - b.lineSequence
  }

  private fun validateOffenderSupported(prisonerDetails: PrisonerDetails): List<ValidationMessage> {
    val hasPtdAlert = prisonerDetails.activeAlerts().any {
      it.alertCode == "PTD" &&
        it.alertType == "O"
    }

    if (hasPtdAlert) {
      return listOf(ValidationMessage(PRISONER_SUBJECT_TO_PTD))
    }
    return emptyList()
  }

  private fun validateSupportedSentences(sentencesAndOffences: List<SentenceAndOffences>): List<ValidationMessage> {
    val supportedSentences: List<SentenceCalculationType> = SentenceCalculationType.values()
      .filter { (it.sentenceClazz == AFineSentence::class.java && this.featureToggles.afine) || it.sentenceClazz == SopcSentence::class.java || it.sentenceClazz == ExtendedDeterminateSentence::class.java || it.sentenceClazz == StandardDeterminateSentence::class.java }
    val supportedCategories = listOf("2003", "2020")
    val validationMessages = sentencesAndOffences.filter {
      !supportedSentences.contains(SentenceCalculationType.from(it.sentenceCalculationType)) ||
        !supportedCategories.contains(it.sentenceCategory)
    }
      .map { ValidationMessage(UNSUPPORTED_SENTENCE_TYPE, listOf(it.sentenceCategory, it.sentenceTypeDescription)) }
      .toMutableList()
    return validationMessages.toList()
  }

  private fun validateFineSentenceSupported(prisonApiSourceData: PrisonApiSourceData): List<ValidationMessage> {
    val validationMessages = mutableListOf<ValidationMessage>()
    val fineSentences =
      prisonApiSourceData.sentenceAndOffences.filter { SentenceCalculationType.from(it.sentenceCalculationType)?.sentenceClazz == AFineSentence::class.java }
    if (fineSentences.isNotEmpty()) {
      if (prisonApiSourceData.offenderFinePayments.isNotEmpty()) {
        validationMessages.add(ValidationMessage(A_FINE_SENTENCE_WITH_PAYMENTS))
      }
      if (fineSentences.any { it.consecutiveToSequence != null }) {
        validationMessages.add(ValidationMessage(A_FINE_SENTENCE_CONSECUTIVE_TO))
      }
      val sequenceToSentenceMap = prisonApiSourceData.sentenceAndOffences.associateBy { it.sentenceSequence }
      if (prisonApiSourceData.sentenceAndOffences.any {
        it.consecutiveToSequence != null && fineSentences.contains(sequenceToSentenceMap[(it.consecutiveToSequence)])
      }
      ) {
        validationMessages.add(ValidationMessage(A_FINE_SENTENCE_CONSECUTIVE))
      }
    }
    return validationMessages
  }

  private fun validateOffenceDateAfterSentenceDate(
    sentencesAndOffence: SentenceAndOffences
  ): ValidationMessage? {
    val invalid =
      sentencesAndOffence.offences.any { it.offenceStartDate != null && it.offenceStartDate > sentencesAndOffence.sentenceDate }
    if (invalid) {
      return ValidationMessage(OFFENCE_DATE_AFTER_SENTENCE_START_DATE, getCaseSeqAndLineSeq(sentencesAndOffence))
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

    val adas = booking.adjustments.getOrEmptyList(AdjustmentType.ADDITIONAL_DAYS_AWARDED).toSet()
    val radas = booking.adjustments.getOrEmptyList(AdjustmentType.RESTORATION_OF_ADDITIONAL_DAYS_AWARDED).toSet()
    val uals = booking.adjustments.getOrEmptyList(AdjustmentType.UNLAWFULLY_AT_LARGE).toSet()
    val adjustments = adas + radas + uals

    val adjustmentsAfterRelease =
      adjustments.filter { it.appliesToSentencesFrom.isAfter(latestReleaseDatePreAddedDays) }.toSet()
    if (adjustmentsAfterRelease.isNotEmpty()) {
      val anyAda = adjustmentsAfterRelease.intersect(adas).isNotEmpty()
      val anyRada = adjustmentsAfterRelease.intersect(radas).isNotEmpty()
      val anyUal = adjustmentsAfterRelease.intersect(uals).isNotEmpty()

      if (anyAda)
        throw AdjustmentIsAfterReleaseDateException(ADJUSTMENT_AFTER_RELEASE_ADA.message, ADJUSTMENT_AFTER_RELEASE_ADA)
      if (anyRada)
        throw AdjustmentIsAfterReleaseDateException(
          ADJUSTMENT_AFTER_RELEASE_RADA.message,
          ADJUSTMENT_AFTER_RELEASE_RADA
        )
      if (anyUal)
        throw AdjustmentIsAfterReleaseDateException(ADJUSTMENT_AFTER_RELEASE_UAL.message, ADJUSTMENT_AFTER_RELEASE_UAL)
    }
  }

  private fun validateRemandOverlappingSentences(booking: Booking) {
    val remandPeriods = booking.adjustments.getOrEmptyList(AdjustmentType.REMAND)
    if (remandPeriods.isNotEmpty()) {
      val remandRanges = remandPeriods.map { LocalDateRange.of(it.fromDate, it.toDate) }
      val sentenceRanges = booking.getAllExtractableSentences()
        .map { LocalDateRange.of(it.sentencedAt, it.sentenceCalculation.adjustedDeterminateReleaseDate) }

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
            val args = listOf(previousRange!!.toString(), it.toString())
            log.error(String.format("Remand of range %s overlaps with remand of range %s", *args.toTypedArray()))
            throw RemandPeriodOverlapsWithRemandException(REMAND_OVERLAPS_WITH_REMAND.message)
          } else {
            throw RemandPeriodOverlapsWithSentenceException(REMAND_OVERLAPS_WITH_SENTENCE.message)
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
        val hasRemand =
          latestReleaseDateSentence.sentenceCalculation.getAdjustmentBeforeSentence(AdjustmentType.REMAND) != 0
        val hasTaggedBail =
          latestReleaseDateSentence.sentenceCalculation.getAdjustmentBeforeSentence(AdjustmentType.TAGGED_BAIL) != 0
        if (hasRemand) {
          throw CustodialPeriodExtinguishedException(
            CUSTODIAL_PERIOD_EXTINGUISHED_REMAND.message,
            CUSTODIAL_PERIOD_EXTINGUISHED_REMAND
          )
        }
        if (hasTaggedBail) {
          throw CustodialPeriodExtinguishedException(
            CUSTODIAL_PERIOD_EXTINGUISHED_TAGGED_BAIL.message,
            CUSTODIAL_PERIOD_EXTINGUISHED_TAGGED_BAIL
          )
        }
      }
    }
  }

  private fun getCaseSeqAndLineSeq(sentencesAndOffence: SentenceAndOffences) =
    listOf(sentencesAndOffence.caseSequence.toString(), sentencesAndOffence.lineSequence.toString())

  companion object {
    private val BOOKING_ADJUSTMENTS_TO_VALIDATE =
      listOf(ADDITIONAL_DAYS_AWARDED, UNLAWFULLY_AT_LARGE, RESTORED_ADDITIONAL_DAYS_AWARDED)
    private val ADJUSTMENT_FUTURE_DATED_MAP = mapOf(
      ADDITIONAL_DAYS_AWARDED to ADJUSTMENT_FUTURE_DATED_ADA,
      UNLAWFULLY_AT_LARGE to ADJUSTMENT_FUTURE_DATED_UAL,
      RESTORED_ADDITIONAL_DAYS_AWARDED to ADJUSTMENT_FUTURE_DATED_RADA
    )
    private val log = LoggerFactory.getLogger(this::class.java)
  }
}
