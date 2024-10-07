package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.threeten.extra.LocalDateRange
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.FeatureToggles
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.SDS40TrancheConfiguration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AFineSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.BotusSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculableSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationUserInputs
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ConsecutiveSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.DetentionAndTrainingOrderSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ExtendedDeterminateSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RecallType.FIXED_TERM_RECALL_14
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RecallType.FIXED_TERM_RECALL_28
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceAndOffenceWithReleaseArrangements
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SopcSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.StandardDeterminateSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Term
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BookingAdjustment
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BookingAdjustmentType.ADDITIONAL_DAYS_AWARDED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BookingAdjustmentType.LAWFULLY_AT_LARGE
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BookingAdjustmentType.RESTORED_ADDITIONAL_DAYS_AWARDED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BookingAdjustmentType.SPECIAL_REMISSION
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BookingAdjustmentType.UNLAWFULLY_AT_LARGE
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BookingAndSentenceAdjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.FixedTermRecallDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonApiSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonerDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAdjustment
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAdjustmentType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAndOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType.BOTUS
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType.Companion.from
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType.DTO
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType.DTO_ORA
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType.FTR_14_ORA
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceTerms
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.manageoffencesapi.Offence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.ImportantDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.ImportantDates.PCSC_COMMENCEMENT_DATE
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.ManageOffencesApiClient
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.SentencesExtractionService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.isAfterOrEqualTo
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.ADJUSTMENT_AFTER_RELEASE_ADA
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.ADJUSTMENT_AFTER_RELEASE_RADA
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.ADJUSTMENT_FUTURE_DATED_ADA
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.ADJUSTMENT_FUTURE_DATED_RADA
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.ADJUSTMENT_FUTURE_DATED_UAL
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.A_FINE_SENTENCE_CONSECUTIVE
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.A_FINE_SENTENCE_CONSECUTIVE_TO
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.A_FINE_SENTENCE_MISSING_FINE_AMOUNT
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.A_FINE_SENTENCE_WITH_PAYMENTS
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.BOTUS_CONSECUTIVE_OR_CONCURRENT_TO_OTHER_SENTENCE
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.CUSTODIAL_PERIOD_EXTINGUISHED_REMAND
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.CUSTODIAL_PERIOD_EXTINGUISHED_TAGGED_BAIL
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.DTO_CONSECUTIVE_TO_SENTENCE
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.DTO_HAS_SENTENCE_CONSECUTIVE_TO_IT
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.EDS18_EDS21_EDSU18_SENTENCE_TYPE_INCORRECT
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.EDS_LICENCE_TERM_LESS_THAN_ONE_YEAR
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.EDS_LICENCE_TERM_MORE_THAN_EIGHT_YEARS
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.FTR_14_DAYS_AGGREGATE_GE_12_MONTHS
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.FTR_14_DAYS_SENTENCE_GE_12_MONTHS
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.FTR_28_DAYS_AGGREGATE_LT_12_MONTHS
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.FTR_28_DAYS_SENTENCE_LT_12_MONTHS
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.FTR_SENTENCES_CONFLICT_WITH_EACH_OTHER
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.FTR_TYPE_14_DAYS_AGGREGATE_GE_12_MONTHS
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.FTR_TYPE_14_DAYS_BUT_LENGTH_IS_28
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.FTR_TYPE_14_DAYS_SENTENCE_GE_12_MONTHS
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.FTR_TYPE_28_DAYS_AGGREGATE_LT_12_MONTHS
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.FTR_TYPE_28_DAYS_BUT_LENGTH_IS_14
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.FTR_TYPE_28_DAYS_SENTENCE_LT_12_MONTHS
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.LASPO_AR_SENTENCE_TYPE_INCORRECT
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.MORE_THAN_ONE_IMPRISONMENT_TERM
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.MORE_THAN_ONE_LICENCE_TERM
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.MULTIPLE_SENTENCES_CONSECUTIVE_TO
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.OFFENCE_DATE_AFTER_SENTENCE_RANGE_DATE
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.OFFENCE_DATE_AFTER_SENTENCE_START_DATE
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.OFFENCE_MISSING_DATE
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.PRE_PCSC_DTO_WITH_ADJUSTMENT
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.PRISONER_SUBJECT_TO_PTD
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.REMAND_FROM_TO_DATES_REQUIRED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.REMAND_OVERLAPS_WITH_REMAND
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.REMAND_OVERLAPS_WITH_SENTENCE
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.SDS_EARLY_RELEASE_UNSUPPORTED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.SEC236A_SENTENCE_TYPE_INCORRECT
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.SEC_91_SENTENCE_TYPE_INCORRECT
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.SENTENCE_HAS_MULTIPLE_TERMS
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.SENTENCE_HAS_NO_IMPRISONMENT_TERM
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.SENTENCE_HAS_NO_LICENCE_TERM
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.SOPC18_SOPC21_SENTENCE_TYPE_INCORRECT
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.SOPC_LICENCE_TERM_NOT_12_MONTHS
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.UNSUPPORTED_ADJUSTMENT_LAWFULLY_AT_LARGE
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.UNSUPPORTED_ADJUSTMENT_SPECIAL_REMISSION
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.UNSUPPORTED_BREACH_97
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.UNSUPPORTED_CALCULATION_DTO_WITH_RECALL
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.UNSUPPORTED_OFFENCE_ENCOURAGING_OR_ASSISTING
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.UNSUPPORTED_SDS40_RECALL_SENTENCE_TYPE
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.UNSUPPORTED_SENTENCE_TYPE
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.UNSUPPORTED_SUSPENDED_OFFENCE
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.ZERO_IMPRISONMENT_TERM
import java.time.LocalDate
import java.time.Period
import java.time.temporal.ChronoUnit.MONTHS

@Service
class ValidationService(
  private val extractionService: SentencesExtractionService,
  private val featureToggles: FeatureToggles,
  private val trancheConfiguration: SDS40TrancheConfiguration,
  private val manageOffencesApiClient: ManageOffencesApiClient,
) {
  fun validateBeforeCalculation(
    sourceData: PrisonApiSourceData,
    calculationUserInputs: CalculationUserInputs,
  ): List<ValidationMessage> {
    log.info("Pre-calculation validation of source data")
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

    val unsupportedOffenceMessages = validateUnsupportedOffences(sentencesAndOffences)
    if (unsupportedOffenceMessages.isNotEmpty()) {
      return unsupportedOffenceMessages
    }

    if (featureToggles.sdsEarlyReleaseUnsupported) {
      val unsupportedEarlyReleaseCalculationMessages = validateSdsEarlyRelease(sourceData)
      if (unsupportedEarlyReleaseCalculationMessages.isNotEmpty()) {
        return unsupportedEarlyReleaseCalculationMessages
      }
    }

    val validationMessages = validateSentences(sortedSentences)
    validationMessages += validateAdjustments(adjustments)
    validationMessages += validateFixedTermRecall(sourceData)
    validationMessages += validatePrePcscDtoDoesNotHaveRemandOrTaggedBail(sourceData)

    return validationMessages
  }

  fun validateSupportedSentencesAndCalculations(sourceData: PrisonApiSourceData): List<ValidationMessage> {
    val sentencesAndOffences = sourceData.sentenceAndOffences
    val sortedSentences = sentencesAndOffences.sortedWith(this::sortByCaseNumberAndLineSequence)
    val validationMessages = mutableListOf<ValidationMessage>()
    validationMessages += validateSupportedSentences(sortedSentences)
    // TODO implement feature toggle for TORERA validation
    validationMessages += validateToreraExempt(sourceData.sentenceAndOffences)
    if (validationMessages.isEmpty()) {
      validationMessages += validateUnsupportedCalculation(sourceData)
    }
    if (validationMessages.isEmpty() && featureToggles.sdsEarlyReleaseUnsupported) {
      validationMessages += validateSdsEarlyRelease(sourceData)
    }
    return validationMessages.toList()
  }

  private fun validatePrePcscDtoDoesNotHaveRemandOrTaggedBail(sourceData: PrisonApiSourceData): List<ValidationMessage> {
    val messages = mutableListOf<ValidationMessage>()
    val adjustments = mutableSetOf<SentenceAdjustmentType>()
    sourceData.bookingAndSentenceAdjustments.sentenceAdjustments.forEach { adjustment ->
      if (adjustment.type == SentenceAdjustmentType.REMAND || adjustment.type == SentenceAdjustmentType.TAGGED_BAIL) {
        val sentence = sourceData.sentenceAndOffences.firstOrNull { it.sentenceSequence == adjustment.sentenceSequence }
        if (sentence != null && SentenceCalculationType.from(sentence.sentenceCalculationType).sentenceClazz == DetentionAndTrainingOrderSentence::class.java && sentence.sentenceDate.isBefore(
            PCSC_COMMENCEMENT_DATE,
          )
        ) {
          adjustments.add(adjustment.type)
        }
      }
    }
    if (adjustments.size > 0) {
      val adjustmentString = adjustments.joinToString(separator = " and ") { it.toString().lowercase() }
      messages.add(ValidationMessage(PRE_PCSC_DTO_WITH_ADJUSTMENT, listOf(adjustmentString.replace("_", " "))))
    }
    return messages
  }

  /*
    Run the validation that can only happen after calculations. e.g. validate that adjustments happen before release date
   */
  fun validateBookingAfterCalculation(
    booking: Booking,
    standardSDSBooking: Booking? = null,
  ): List<ValidationMessage> {
    log.info("Validating booking after calculation")
    val messages = mutableListOf<ValidationMessage>()
    booking.sentenceGroups.forEach { messages += validateSentenceHasNotBeenExtinguished(it) }
    messages += validateRemandOverlappingRemand(booking)
    messages += validateRemandOverlappingSentences(standardSDSBooking ?: booking, booking)
    messages += validateAdditionAdjustmentsInsideLatestReleaseDate(standardSDSBooking ?: booking, booking)
    messages += validateFixedTermRecallAfterCalc(booking)
    messages += validateUnsupportedRecallTypes(booking)

    return messages
  }

  private fun validateUnsupportedRecallTypes(
    booking: Booking,
  ): List<ValidationMessage> {
    var result = emptyList<ValidationMessage>()
    booking.getAllExtractableSentences().any {
      it.releaseDateTypes.contains(ReleaseDateType.TUSED) &&
        (
          it is StandardDeterminateSentence ||
            (it is ConsecutiveSentence && it.orderedSentences.any { sentence -> sentence is StandardDeterminateSentence })
          ) &&
        it.recallType != null &&
        it.sentenceCalculation.adjustedHistoricDeterminateReleaseDate.isAfterOrEqualTo(trancheConfiguration.trancheOneCommencementDate)
    }.takeIf { it }?.let {
      result = listOf(
        ValidationMessage(
          UNSUPPORTED_SDS40_RECALL_SENTENCE_TYPE,
        ),
      )
    }
    return result
  }

  private fun validateFixedTermRecall(sourceData: PrisonApiSourceData): List<ValidationMessage> {
    val sentencesAndOffences = sourceData.sentenceAndOffences
    val ftrDetails = sourceData.fixedTermRecallDetails ?: return emptyList()
    val (recallLength, has14DayFTRSentence, has28DayFTRSentence) = getFtrValidationDetails(
      ftrDetails,
      sentencesAndOffences,
    )

    if (has14DayFTRSentence && has28DayFTRSentence) {
      return listOf(
        ValidationMessage(
          FTR_SENTENCES_CONFLICT_WITH_EACH_OTHER,
        ),
      )
    }
    if (has14DayFTRSentence && recallLength == 28) return listOf(ValidationMessage(FTR_TYPE_14_DAYS_BUT_LENGTH_IS_28))
    if (has28DayFTRSentence && recallLength == 14) return listOf(ValidationMessage(FTR_TYPE_28_DAYS_BUT_LENGTH_IS_14))
    return emptyList()
  }

  fun validateBeforeCalculation(booking: Booking): List<ValidationMessage> {
    return validateFixedTermRecall(booking)
  }

  private fun validateFixedTermRecall(booking: Booking): List<ValidationMessage> {
    val ftrDetails = booking.fixedTermRecallDetails ?: return emptyList()
    val validationMessages = mutableListOf<ValidationMessage>()
    val ftrSentences = booking.sentences.filter {
      it.recallType == FIXED_TERM_RECALL_14 || it.recallType == FIXED_TERM_RECALL_28
    }
    val (ftr28Sentences, ftr14Sentences) = ftrSentences.partition { it.recallType == FIXED_TERM_RECALL_28 }

    val ftrSentencesUuids = ftrSentences.map { it.identifier }
    val recallLength = ftrDetails.recallLength

    val maxFtrSentence = ftrSentences.maxBy { it.getLengthInDays() }
    val maxFtrSentenceIsLessThan12Months = maxFtrSentence.durationIsLessThan(TWELVE, MONTHS)
    val ftrSentenceExistsInConsecutiveChain = ftrSentences.any { it.consecutiveSentenceUUIDs.isNotEmpty() } ||
      booking.sentences.any {
        it.consecutiveSentenceUUIDs.toSet().intersect(ftrSentencesUuids.toSet()).isNotEmpty()
      }

    if (!maxFtrSentenceIsLessThan12Months && recallLength == 14) {
      validationMessages += ValidationMessage(FTR_14_DAYS_SENTENCE_GE_12_MONTHS)
    }

    if (maxFtrSentenceIsLessThan12Months && recallLength == 28 && !ftrSentenceExistsInConsecutiveChain) {
      validationMessages += ValidationMessage(FTR_28_DAYS_SENTENCE_LT_12_MONTHS)
    }

    if (ftr28Sentences.isNotEmpty() && maxFtrSentenceIsLessThan12Months && !ftrSentenceExistsInConsecutiveChain) {
      validationMessages += ValidationMessage(FTR_TYPE_28_DAYS_SENTENCE_LT_12_MONTHS)
    }

    if (ftr14Sentences.any { it.durationIsGreaterThanOrEqualTo(TWELVE, MONTHS) }) {
      validationMessages += ValidationMessage(FTR_TYPE_14_DAYS_SENTENCE_GE_12_MONTHS)
    }

    return validationMessages
  }

  private fun validateFixedTermRecallAfterCalc(booking: Booking): List<ValidationMessage> {
    val messages = mutableListOf<ValidationMessage>()
    val ftrDetails = booking.fixedTermRecallDetails ?: return messages
    val recallLength = ftrDetails.recallLength

    booking.consecutiveSentences.forEach {
      if (it.recallType == FIXED_TERM_RECALL_14 || it.recallType == FIXED_TERM_RECALL_28) {
        if (recallLength == 14 && it.durationIsGreaterThanOrEqualTo(TWELVE, MONTHS)) {
          messages += ValidationMessage(FTR_14_DAYS_AGGREGATE_GE_12_MONTHS)
        }
      }
    }

    booking.consecutiveSentences.forEach {
      if (it.recallType == FIXED_TERM_RECALL_14 || it.recallType == FIXED_TERM_RECALL_28) {
        if (recallLength == 28 && it.durationIsLessThan(TWELVE, MONTHS)) {
          messages += ValidationMessage(
            FTR_28_DAYS_AGGREGATE_LT_12_MONTHS,
          )
        }
      }
    }

    booking.consecutiveSentences.forEach {
      if (it.recallType == FIXED_TERM_RECALL_28 && it.durationIsLessThan(TWELVE, MONTHS)) {
        messages += ValidationMessage(FTR_TYPE_28_DAYS_AGGREGATE_LT_12_MONTHS)
      }
    }

    booking.consecutiveSentences.forEach {
      if (it.recallType == FIXED_TERM_RECALL_14 && it.durationIsGreaterThanOrEqualTo(TWELVE, MONTHS)) {
        messages += ValidationMessage(FTR_TYPE_14_DAYS_AGGREGATE_GE_12_MONTHS)
      }
    }
    return messages
  }

  private fun getFtrValidationDetails(
    ftrDetails: FixedTermRecallDetails,
    sentencesAndOffences: List<SentenceAndOffence>,
  ): Triple<Int, Boolean, Boolean> {
    val recallLength = ftrDetails.recallLength
    val bookingsSentenceTypes = sentencesAndOffences.map { from(it.sentenceCalculationType) }
    val has14DayFTRSentence = bookingsSentenceTypes.any { it == FTR_14_ORA }
    val has28DayFTRSentence = SentenceCalculationType.entries.any {
      it.isFixedTermRecall && it != FTR_14_ORA && bookingsSentenceTypes.contains(it)
    }
    return Triple(recallLength, has14DayFTRSentence, has28DayFTRSentence)
  }

  private fun validateUnsupportedCalculation(sourceData: PrisonApiSourceData): List<ValidationMessage> {
    val messages = validateFineSentenceSupported(sourceData).toMutableList()
    messages += validateSupportedAdjustments(sourceData.bookingAndSentenceAdjustments.bookingAdjustments)
    messages += validateDtoIsNotRecall(sourceData)
    messages += validateDtoIsNotConsecutiveToSentence(sourceData)
    messages += validateBotusWithOtherSentence(sourceData)
    return messages
  }

  private fun findUnsupportedEncouragingOffenceCodes(sentenceAndOffences: List<SentenceAndOffenceWithReleaseArrangements>): List<SentenceAndOffence> {
    val offenceCodesToFilter = (2..13).map { "SC070${"%02d".format(it)}" }
    return sentenceAndOffences.filter { it.offence.offenceCode in offenceCodesToFilter }
  }

  private fun findUnsupported97BreachOffencesAfter1Dec2020(sentencesAndOffence: List<SentenceAndOffenceWithReleaseArrangements>): List<SentenceAndOffence> {
    return sentencesAndOffence.filter {
      it.offence.offenceCode.startsWith("PH97003") && it.offence.offenceStartDate != null &&
        it.offence.offenceStartDate.isAfterOrEqualTo(AFTER_97_BREACH_PROVISION_INVALID)
    }
  }

  private fun findUnsupportedSuspendedOffenceCodes(sentenceAndOffences: List<SentenceAndOffenceWithReleaseArrangements>): List<SentenceAndOffence> {
    val offenceCodesToFilter = listOf("SE20512", "CJ03523")
    return sentenceAndOffences.filter { it.offence.offenceCode in offenceCodesToFilter }
  }

  private fun validateUnsupportedOffences(sentencesAndOffence: List<SentenceAndOffenceWithReleaseArrangements>): List<ValidationMessage> {
    val messages = validateUnsupportedEncouragingOffences(sentencesAndOffence).toMutableList()
    messages += validateUnsupported97BreachOffencesAfter1Dec2020(sentencesAndOffence)
    messages += validateUnsupportedSuspendedOffences(sentencesAndOffence)
    return messages
  }

  private fun validateUnsupportedSuspendedOffences(sentencesAndOffence: List<SentenceAndOffenceWithReleaseArrangements>): List<ValidationMessage> {
    val unSupportedEncouragingOffenceCodes = findUnsupportedSuspendedOffenceCodes(sentencesAndOffence)
    if (unSupportedEncouragingOffenceCodes.isNotEmpty()) {
      return listOf(ValidationMessage(UNSUPPORTED_SUSPENDED_OFFENCE))
    }
    return emptyList()
  }

  private fun validateUnsupportedEncouragingOffences(sentencesAndOffence: List<SentenceAndOffenceWithReleaseArrangements>): List<ValidationMessage> {
    val unSupportedEncouragingOffenceCodes = findUnsupportedEncouragingOffenceCodes(sentencesAndOffence)
    if (unSupportedEncouragingOffenceCodes.isNotEmpty()) {
      return listOf(ValidationMessage(UNSUPPORTED_OFFENCE_ENCOURAGING_OR_ASSISTING))
    }
    return emptyList()
  }

  private fun validateUnsupported97BreachOffencesAfter1Dec2020(sentencesAndOffence: List<SentenceAndOffenceWithReleaseArrangements>): List<ValidationMessage> {
    val unSupportedEncouragingOffenceCodes = findUnsupported97BreachOffencesAfter1Dec2020(sentencesAndOffence)
    if (unSupportedEncouragingOffenceCodes.isNotEmpty()) {
      return listOf(ValidationMessage(UNSUPPORTED_BREACH_97))
    }
    return emptyList()
  }

  private fun validateBotusWithOtherSentence(sourceData: PrisonApiSourceData): List<ValidationMessage> {
    val validationMessages = mutableListOf<ValidationMessage>()
    val botusSentences =
      sourceData.sentenceAndOffences.filter { SentenceCalculationType.from(it.sentenceCalculationType) == BOTUS }
    if (botusSentences.size > 0 && botusSentences.size != sourceData.sentenceAndOffences.size) {
      validationMessages.add(ValidationMessage(code = BOTUS_CONSECUTIVE_OR_CONCURRENT_TO_OTHER_SENTENCE))
    }
    return validationMessages
  }

  private fun validateDtoIsNotConsecutiveToSentence(sourceData: PrisonApiSourceData): List<ValidationMessage> {
    val validationMessages = mutableListOf<ValidationMessage>()
    sourceData.sentenceAndOffences.forEach {
      val isDto =
        SentenceCalculationType.from(it.sentenceCalculationType).sentenceClazz == DetentionAndTrainingOrderSentence::class.java
      if (isDto) {
        if (it.consecutiveToSequence != null && sequenceNotDto(it.consecutiveToSequence, sourceData)) {
          validationMessages.add(ValidationMessage(code = DTO_CONSECUTIVE_TO_SENTENCE))
        }
        if (sourceData.sentenceAndOffences.any { sent ->
            (
              sent.consecutiveToSequence == it.sentenceSequence && SentenceCalculationType.from(
                sent.sentenceCalculationType,
              ).sentenceClazz != DetentionAndTrainingOrderSentence::class.java
              )
          }
        ) {
          validationMessages.add(ValidationMessage(code = DTO_HAS_SENTENCE_CONSECUTIVE_TO_IT))
        }
      }
    }

    return validationMessages.toList()
  }

  private fun sequenceNotDto(consecutiveSequence: Int, sourceData: PrisonApiSourceData): Boolean {
    val consecutiveTo = sourceData.sentenceAndOffences.firstOrNull { it.sentenceSequence == consecutiveSequence }
    return consecutiveTo != null && SentenceCalculationType.from(consecutiveTo.sentenceCalculationType).sentenceClazz != DetentionAndTrainingOrderSentence::class.java
  }

  private fun validateSentences(sentences: List<SentenceAndOffence>): MutableList<ValidationMessage> {
    val validationMessages = sentences.map { validateSentence(it) }.flatten().toMutableList()
    validationMessages += validateConsecutiveSentenceUnique(sentences)
    return validationMessages
  }

  fun validateSentenceForManualEntry(sentences: List<SentenceAndOffence>): MutableList<ValidationMessage> {
    return sentences.map { validateSentenceForManualEntry(it) }.flatten().toMutableList()
  }

  private data class ValidateConsecutiveSentenceUniqueRecord(
    val consecutiveToSequence: Int,
    val lineSequence: Int,
    val caseSequence: Int,
  )

  private fun validateConsecutiveSentenceUnique(sentences: List<SentenceAndOffence>): List<ValidationMessage> {
    val consecutiveSentences = sentences.filter { it.consecutiveToSequence != null }
      .map { ValidateConsecutiveSentenceUniqueRecord(it.consecutiveToSequence!!, it.lineSequence, it.caseSequence) }
      .distinct()
    val sentencesGroupedByConsecutiveTo = consecutiveSentences.groupBy { it.consecutiveToSequence }
    return sentencesGroupedByConsecutiveTo.entries.filter { it.value.size > 1 }.map { entry ->
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
    if (adjustments.any { it.type == LAWFULLY_AT_LARGE }) {
      messages.add(
        ValidationMessage(
          UNSUPPORTED_ADJUSTMENT_LAWFULLY_AT_LARGE,
        ),
      )
    }
    if (adjustments.any { it.type == SPECIAL_REMISSION }) {
      messages.add(
        ValidationMessage(
          UNSUPPORTED_ADJUSTMENT_SPECIAL_REMISSION,
        ),
      )
    }
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
          val messageArgs =
            listOf(it.start.toString(), it.end.toString(), totalRange!!.start.toString(), totalRange!!.end.toString())
          return listOf(ValidationMessage(REMAND_OVERLAPS_WITH_REMAND, arguments = messageArgs))
        }
      }
    }
    return emptyList()
  }

  private fun validateBookingAdjustment(bookingAdjustments: List<BookingAdjustment>): List<ValidationMessage> =
    bookingAdjustments.filter {
      val dateToValidate = if (it.type == UNLAWFULLY_AT_LARGE && it.toDate != null) it.toDate else it.fromDate
      BOOKING_ADJUSTMENTS_TO_VALIDATE.contains(it.type) && dateToValidate.isAfter(LocalDate.now())
    }.map { it.type }.distinct().map { ValidationMessage(ADJUSTMENT_FUTURE_DATED_MAP[it]!!) }

  private fun validateSentenceAdjustment(sentenceAdjustment: SentenceAdjustment): ValidationMessage? {
    if (sentenceAdjustment.type == SentenceAdjustmentType.REMAND && (sentenceAdjustment.fromDate == null || sentenceAdjustment.toDate == null)) {
      return ValidationMessage(REMAND_FROM_TO_DATES_REQUIRED)
    }
    return null
  }

  private fun validateSentence(it: SentenceAndOffence): List<ValidationMessage> {
    return listOfNotNull(
      validateWithoutOffenceDate(it),
      validateOffenceDateAfterSentenceDate(it),
      validateOffenceRangeDateAfterSentenceDate(it),
    ) + validateDuration(it) + listOfNotNull(
      validateThatSec91SentenceTypeCorrectlyApplied(it),
      validateEdsSentenceTypesCorrectlyApplied(it),
      validateSopcSentenceTypesCorrectlyApplied(it),
      validateFineAmount(it),
    )
  }

  private fun validateSentenceForManualEntry(it: SentenceAndOffence): List<ValidationMessage> {
    return listOfNotNull(
      validateWithoutOffenceDate(it),
    )
  }

  private fun validateFineAmount(sentencesAndOffence: SentenceAndOffence): ValidationMessage? {
    val sentenceCalculationType = SentenceCalculationType.from(sentencesAndOffence.sentenceCalculationType)
    if (sentenceCalculationType.sentenceClazz == AFineSentence::class.java) {
      if (sentencesAndOffence.fineAmount == null) {
        return ValidationMessage(A_FINE_SENTENCE_MISSING_FINE_AMOUNT, getCaseSeqAndLineSeq(sentencesAndOffence))
      }
    }
    return null
  }

  private fun validateThatSec91SentenceTypeCorrectlyApplied(sentencesAndOffence: SentenceAndOffence): ValidationMessage? {
    val sentenceCalculationType = SentenceCalculationType.from(sentencesAndOffence.sentenceCalculationType)

    if (sentenceCalculationType == SentenceCalculationType.SEC91_03 || sentenceCalculationType == SentenceCalculationType.SEC91_03_ORA) {
      if (sentencesAndOffence.sentenceDate.isAfterOrEqualTo(ImportantDates.SEC_91_END_DATE)) {
        return ValidationMessage(SEC_91_SENTENCE_TYPE_INCORRECT, getCaseSeqAndLineSeq(sentencesAndOffence))
      }
    }
    return null
  }

  private fun validateEdsSentenceTypesCorrectlyApplied(sentencesAndOffence: SentenceAndOffence): ValidationMessage? {
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
          getCaseSeqAndLineSeq(sentencesAndOffence),
        )
      }
    } else if (sentenceCalculationType == SentenceCalculationType.LASPO_AR) {
      if (sentencesAndOffence.sentenceDate.isAfterOrEqualTo(ImportantDates.LASPO_AR_SENTENCE_TYPES_END_DATE)) {
        return ValidationMessage(LASPO_AR_SENTENCE_TYPE_INCORRECT, getCaseSeqAndLineSeq(sentencesAndOffence))
      }
    }
    return null
  }

  private fun validateSopcSentenceTypesCorrectlyApplied(sentencesAndOffence: SentenceAndOffence): ValidationMessage? {
    val sentenceCalculationType = SentenceCalculationType.from(sentencesAndOffence.sentenceCalculationType)

    if (listOf(SentenceCalculationType.SOPC18, SentenceCalculationType.SOPC21).contains(sentenceCalculationType)) {
      if (sentencesAndOffence.sentenceDate.isBefore(ImportantDates.SEC_91_END_DATE)) {
        return ValidationMessage(
          SOPC18_SOPC21_SENTENCE_TYPE_INCORRECT,
          getCaseSeqAndLineSeq(sentencesAndOffence),
        )
      }
    } else if (sentenceCalculationType == SentenceCalculationType.SEC236A) {
      if (sentencesAndOffence.sentenceDate.isAfterOrEqualTo(ImportantDates.SEC_91_END_DATE)) {
        return ValidationMessage(SEC236A_SENTENCE_TYPE_INCORRECT, getCaseSeqAndLineSeq(sentencesAndOffence))
      }
    }
    return null
  }

  private fun validateDuration(sentencesAndOffence: SentenceAndOffence): List<ValidationMessage> {
    val sentenceCalculationType = SentenceCalculationType.from(sentencesAndOffence.sentenceCalculationType)
    return if (sentenceCalculationType.sentenceClazz == StandardDeterminateSentence::class.java ||
      sentenceCalculationType.sentenceClazz == AFineSentence::class.java ||
      sentenceCalculationType.sentenceClazz == DetentionAndTrainingOrderSentence::class.java ||
      sentenceCalculationType.sentenceClazz == BotusSentence::class.java
    ) {
      validateSingleTermDuration(sentencesAndOffence)
    } else {
      validateImprisonmentAndLicenceTermDuration(sentencesAndOffence)
    }
  }

  private fun validateSingleTermDuration(sentencesAndOffence: SentenceAndOffence): List<ValidationMessage> {
    val validationMessages = mutableListOf<ValidationMessage>()
    val hasMultipleTerms = sentencesAndOffence.terms.size > 1
    if (hasMultipleTerms) {
      validationMessages.add(
        ValidationMessage(
          SENTENCE_HAS_MULTIPLE_TERMS,
          getCaseSeqAndLineSeq(sentencesAndOffence),
        ),
      )
    } else {
      val emptyImprisonmentTerm =
        sentencesAndOffence.terms[0].days == 0 && sentencesAndOffence.terms[0].weeks == 0 && sentencesAndOffence.terms[0].months == 0 && sentencesAndOffence.terms[0].years == 0

      if (emptyImprisonmentTerm) {
        validationMessages.add(
          ValidationMessage(
            ZERO_IMPRISONMENT_TERM,
            getCaseSeqAndLineSeq(sentencesAndOffence),
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
          SENTENCE_HAS_NO_IMPRISONMENT_TERM,
          getCaseSeqAndLineSeq(sentencesAndOffence),
        ),
      )
    } else if (imprisonmentTerms.size > 1) {
      validationMessages.add(
        ValidationMessage(
          MORE_THAN_ONE_IMPRISONMENT_TERM,
          getCaseSeqAndLineSeq(sentencesAndOffence),
        ),
      )
    } else {
      val emptyTerm =
        imprisonmentTerms[0].days == 0 && imprisonmentTerms[0].weeks == 0 && imprisonmentTerms[0].months == 0 && imprisonmentTerms[0].years == 0
      if (emptyTerm) {
        validationMessages.add(
          ValidationMessage(
            ZERO_IMPRISONMENT_TERM,
            getCaseSeqAndLineSeq(sentencesAndOffence),
          ),
        )
      }
    }

    val licenceTerms = sentencesAndOffence.terms.filter { it.code == SentenceTerms.LICENCE_TERM_CODE }
    if (licenceTerms.isEmpty()) {
      validationMessages.add(
        ValidationMessage(
          SENTENCE_HAS_NO_LICENCE_TERM,
          getCaseSeqAndLineSeq(sentencesAndOffence),
        ),
      )
    } else if (licenceTerms.size > 1) {
      validationMessages.add(
        ValidationMessage(
          MORE_THAN_ONE_LICENCE_TERM,
          getCaseSeqAndLineSeq(sentencesAndOffence),
        ),
      )
    } else {
      val sentenceCalculationType = SentenceCalculationType.from(sentencesAndOffence.sentenceCalculationType)
      if (sentenceCalculationType.sentenceClazz == ExtendedDeterminateSentence::class.java) {
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
              EDS_LICENCE_TERM_LESS_THAN_ONE_YEAR,
              getCaseSeqAndLineSeq(sentencesAndOffence),
            ),
          )
        } else if (endOfDuration.isAfter(endOfEightYears)) {
          validationMessages.add(
            ValidationMessage(
              EDS_LICENCE_TERM_MORE_THAN_EIGHT_YEARS,
              getCaseSeqAndLineSeq(sentencesAndOffence),
            ),
          )
        }
      } else if (sentenceCalculationType.sentenceClazz == SopcSentence::class.java) {
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
              SOPC_LICENCE_TERM_NOT_12_MONTHS,
              getCaseSeqAndLineSeq(sentencesAndOffence),
            ),
          )
        }
      }
    }

    return validationMessages
  }

  private fun validateOffenceRangeDateAfterSentenceDate(sentencesAndOffence: SentenceAndOffence): ValidationMessage? {
    val offence = sentencesAndOffence.offence
    val invalid = offence.offenceEndDate != null && offence.offenceEndDate > sentencesAndOffence.sentenceDate
    if (invalid) {
      return ValidationMessage(OFFENCE_DATE_AFTER_SENTENCE_RANGE_DATE, getCaseSeqAndLineSeq(sentencesAndOffence))
    }
    return null
  }

  private fun validateWithoutOffenceDate(sentencesAndOffence: SentenceAndOffence): ValidationMessage? {
    // It's valid to not have an end date for many offence types, but the start date must always be present in
    // either case. If an end date is null it will be set to the start date in the transformation.
    val invalid = sentencesAndOffence.offence.offenceStartDate == null
    if (invalid) {
      return ValidationMessage(OFFENCE_MISSING_DATE, getCaseSeqAndLineSeq(sentencesAndOffence))
    }
    return null
  }

  private fun sortByCaseNumberAndLineSequence(a: SentenceAndOffence, b: SentenceAndOffence): Int {
    if (a.caseSequence > b.caseSequence) return 1
    if (a.caseSequence < b.caseSequence) return -1
    return a.lineSequence - b.lineSequence
  }

  private fun validateOffenderSupported(prisonerDetails: PrisonerDetails): List<ValidationMessage> {
    val hasPtdAlert = prisonerDetails.activeAlerts().any {
      it.alertCode == "PTD" && it.alertType == "O"
    }

    if (hasPtdAlert) {
      return listOf(ValidationMessage(PRISONER_SUBJECT_TO_PTD))
    }
    return emptyList()
  }

  private fun validateSupportedSentences(sentencesAndOffences: List<SentenceAndOffence>): List<ValidationMessage> {
    val supportedCategories = listOf("2003", "2020")
    val validationMessages = sentencesAndOffences.filter {
      if (SentenceCalculationType.isSupported(it.sentenceCalculationType) && supportedCategories.contains(it.sentenceCategory)) {
        val type = from(it.sentenceCalculationType)
        !featureToggles.botus && type.sentenceClazz == BotusSentence::class.java
      } else {
        true
      }
    }
      .map {
        ValidationMessage(
          UNSUPPORTED_SENTENCE_TYPE,
          listOf(it.sentenceCategory, it.sentenceTypeDescription),
        )
      }
      .toMutableList()
    return validationMessages.toList()
  }

  private fun validateDtoIsNotRecall(prisonApiSourceData: PrisonApiSourceData): List<ValidationMessage> {
    val validationMessages = mutableListOf<ValidationMessage>()
    var bookingHasDto = false
    var bookingHasRecall = false
    prisonApiSourceData.sentenceAndOffences.forEach {
      val sentenceCalculationType = SentenceCalculationType.from(it.sentenceCalculationType)
      val hasDtoRecall = it.terms.any { terms ->
        terms.code == SentenceTerms.BREACH_OF_SUPERVISION_REQUIREMENTS_TERM_CODE || terms.code == SentenceTerms.BREACH_DUE_TO_IMPRISONABLE_OFFENCE_TERM_CODE
      }
      val hasDto = sentenceCalculationType == DTO || sentenceCalculationType == DTO_ORA
      if (hasDto) {
        bookingHasDto = true
      }
      if (sentenceCalculationType.recallType != null) {
        bookingHasRecall = true
      }
      if (hasDto && hasDtoRecall) {
        validationMessages.add(ValidationMessage(ValidationCode.DTO_RECALL))
      } else if (bookingHasDto && bookingHasRecall) {
        validationMessages.add(ValidationMessage(UNSUPPORTED_CALCULATION_DTO_WITH_RECALL))
      }
    }

    return validationMessages.toList()
  }

  private fun validateFineSentenceSupported(prisonApiSourceData: PrisonApiSourceData): List<ValidationMessage> {
    val validationMessages = mutableListOf<ValidationMessage>()
    val fineSentences =
      prisonApiSourceData.sentenceAndOffences.filter { SentenceCalculationType.from(it.sentenceCalculationType).sentenceClazz == AFineSentence::class.java }
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
    sentencesAndOffence: SentenceAndOffence,
  ): ValidationMessage? {
    val offence = sentencesAndOffence.offence
    if (offence.offenceStartDate != null && offence.offenceStartDate > sentencesAndOffence.sentenceDate) {
      return ValidationMessage(OFFENCE_DATE_AFTER_SENTENCE_START_DATE, getCaseSeqAndLineSeq(sentencesAndOffence))
    }
    return null
  }

  private fun getLongestRelevantSentence(
    sentences: List<CalculableSentence>,
    longestSentences: List<CalculableSentence>,
  ): List<CalculableSentence> {
    return sentences.zip(longestSentences).map { (sentence, longestSentence) ->
      if (sentence.sentencedAt.isBefore(trancheConfiguration.trancheTwoCommencementDate)) {
        longestSentence
      } else {
        sentence
      }
    }
  }

  private fun getRelevantSentenceRanges(
    sentences: List<CalculableSentence>,
    longestSentences: List<CalculableSentence>,
  ): List<LocalDateRange> {
    val longestRelevantSentences = sentences.zip(longestSentences).map { (sentence, longestSentence) ->
      if (sentence.sentenceCalculation.adjustedDeterminateReleaseDate.isBefore(trancheConfiguration.trancheOneCommencementDate)) {
        longestSentence
      } else {
        sentence
      }
    }
    return longestRelevantSentences
      .filter { !it.isRecall() }
      .map {
        LocalDateRange.of(
          it.sentencedAt,
          it.sentenceCalculation.unadjustedDeterminateReleaseDate,
        )
      }
  }

  private fun validateAdditionAdjustmentsInsideLatestReleaseDate(
    longestBooking: Booking,
    booking: Booking,
  ): List<ValidationMessage> {
    val sentences = booking.getAllExtractableSentences()
    val longestSentences = longestBooking.getAllExtractableSentences()

    // Ensure both lists have the same size before proceeding
    if (sentences.size != longestSentences.size) {
      throw IllegalArgumentException("The number of sentences in longestBooking and booking must be the same.")
    }

    val longestRelevantSentences = this.getLongestRelevantSentence(sentences, longestSentences)

    val latestReleaseDatePreAddedDays =
      longestRelevantSentences.filter { it !is Term }.maxOfOrNull { it.sentenceCalculation.releaseDateWithoutAdditions }
        ?: return emptyList()

    val adas = booking.adjustments.getOrEmptyList(AdjustmentType.ADDITIONAL_DAYS_AWARDED).toSet()
    val radas = booking.adjustments.getOrEmptyList(AdjustmentType.RESTORATION_OF_ADDITIONAL_DAYS_AWARDED).toSet()
    val adjustments = adas + radas

    val adjustmentsAfterRelease =
      adjustments.filter { it.appliesToSentencesFrom.isAfter(latestReleaseDatePreAddedDays) }.toSet()
    if (adjustmentsAfterRelease.isNotEmpty()) {
      val anyAda = adjustmentsAfterRelease.intersect(adas).isNotEmpty()
      val anyRada = adjustmentsAfterRelease.intersect(radas).isNotEmpty()

      if (anyAda) return listOf(ValidationMessage(ADJUSTMENT_AFTER_RELEASE_ADA))
      if (anyRada) return listOf(ValidationMessage(ADJUSTMENT_AFTER_RELEASE_RADA))
    }
    return emptyList()
  }

  private fun validateRemandOverlappingSentences(longestBooking: Booking, booking: Booking): List<ValidationMessage> {
    val sentences = booking.getAllExtractableSentences()
    val longestSentences = longestBooking.getAllExtractableSentences()

    val remandPeriods = booking.adjustments.getOrEmptyList(AdjustmentType.REMAND)

    val validationMessages = mutableListOf<ValidationMessage>()
    if (remandPeriods.isNotEmpty()) {
      val remandRanges = remandPeriods.map { LocalDateRange.of(it.fromDate, it.toDate) }

      val sentenceRanges = this.getRelevantSentenceRanges(sentences, longestSentences)

      remandRanges.forEach { remandRange ->
        sentenceRanges.forEach { sentenceRange ->
          if (remandRange.isConnected(sentenceRange)) {
            logIntersectionWarning(remandRange, sentenceRange, "Remand of range %s overlaps with sentence of range %s")
            validationMessages.add(
              ValidationMessage(
                REMAND_OVERLAPS_WITH_SENTENCE,
                arguments = buildMessageArguments(sentenceRange, remandRange),
              ),
            )
          }
        }
      }
    }

    return validationMessages
  }

  private fun validateRemandOverlappingRemand(booking: Booking): List<ValidationMessage> {
    val remandPeriods = booking.adjustments.getOrEmptyList(AdjustmentType.REMAND)

    val validationMessages = mutableListOf<ValidationMessage>()
    if (remandPeriods.isNotEmpty()) {
      val remandRanges = remandPeriods.map { LocalDateRange.of(it.fromDate, it.toDate) }

      remandRanges.forEachIndexed { index, remandRange ->
        remandRanges.drop(index + 1).forEach { otherRemandRange ->
          if (remandRange.isConnected(otherRemandRange)) {
            logIntersectionWarning(
              remandRange,
              otherRemandRange,
              "Remand of range %s overlaps with other remand of range %s",
            )
            validationMessages.add(
              ValidationMessage(
                REMAND_OVERLAPS_WITH_REMAND,
                arguments = buildMessageArguments(remandRange, otherRemandRange),
              ),
            )
          }
        }
      }
    }

    return validationMessages
  }

  private fun logIntersectionWarning(range1: LocalDateRange, range2: LocalDateRange, messageTemplate: String) {
    val args = listOf(range1.toString(), range2.toString())
    log.warn(
      String.format(
        messageTemplate,
        *args.toTypedArray(),
      ),
    )
  }

  private fun buildMessageArguments(range1: LocalDateRange, range2: LocalDateRange): List<String> {
    return listOf(
      range1.start.toString(),
      range1.end.toString(),
      range2.start.toString(),
      range2.end.toString(),
    )
  }

  private fun validateSentenceHasNotBeenExtinguished(sentences: List<CalculableSentence>): List<ValidationMessage> {
    val messages = mutableListOf<ValidationMessage>()
    val determinateSentences = sentences.filter { !it.isRecall() }
    if (determinateSentences.isNotEmpty()) {
      val earliestSentenceDate = determinateSentences.minOf { it.sentencedAt }
      val latestReleaseDateSentence = extractionService.mostRecentSentence(
        determinateSentences,
        SentenceCalculation::adjustedUncappedDeterminateReleaseDate,
      )
      if (earliestSentenceDate.minusDays(1)
          .isAfter(latestReleaseDateSentence.sentenceCalculation.adjustedUncappedDeterminateReleaseDate)
      ) {
        val hasRemand =
          latestReleaseDateSentence.sentenceCalculation.getAdjustmentBeforeSentence(AdjustmentType.REMAND) != 0
        val hasTaggedBail =
          latestReleaseDateSentence.sentenceCalculation.getAdjustmentBeforeSentence(AdjustmentType.TAGGED_BAIL) != 0
        if (hasRemand) messages += ValidationMessage(CUSTODIAL_PERIOD_EXTINGUISHED_REMAND)

        if (hasTaggedBail) messages += ValidationMessage(CUSTODIAL_PERIOD_EXTINGUISHED_TAGGED_BAIL)
      }
    }
    return messages
  }

  private fun getCaseSeqAndLineSeq(sentencesAndOffence: SentenceAndOffence) =
    listOf(sentencesAndOffence.caseSequence.toString(), sentencesAndOffence.lineSequence.toString())

  private fun validateSdsEarlyRelease(sourceData: PrisonApiSourceData): List<ValidationMessage> {
    val anySdsExcludingSdsPlus = sourceData.sentenceAndOffences.any {
      val sentenceCalculationType = SentenceCalculationType.from(it.sentenceCalculationType)
      val isSds =
        sentenceCalculationType.sentenceClazz == StandardDeterminateSentence::class.java && sentenceCalculationType.recallType == null
      isSds && !it.isSDSPlus
    }
    if (anySdsExcludingSdsPlus) {
      return listOf(ValidationMessage(SDS_EARLY_RELEASE_UNSUPPORTED))
    }
    return emptyList()
  }

  private fun validateToreraExempt(sentenceAndOffences: List<SentenceAndOffenceWithReleaseArrangements>): List<ValidationMessage> {
    val messages = mutableListOf<ValidationMessage>()
    val schedule = manageOffencesApiClient.getScheduleOffences(5)
    val schedule19ZACodes = schedule.scheduleParts.filter { it.offences is List<Offence> }
      .flatMap { it.offences!! }
      .map { it.code }

    /**
     * Any SDS sentences with a sentence date greater than 2005-04-04 must not include any offence codes
     * present within Schedule 19ZA
     */
    val sdsCodes = sentenceAndOffences
      .filter {
        listOf(
          SentenceCalculationType.ADIMP.name,
          SentenceCalculationType.ADIMP_ORA.name,
          SentenceCalculationType.SEC250.name,
          SentenceCalculationType.SEC250_ORA.name,
          SentenceCalculationType.YOI.name,
          SentenceCalculationType.YOI_ORA.name,
        ).contains(it.sentenceCalculationType) && it.sentenceDate > LocalDate.parse("2005-04-04")
      }
      .map { it.offence.offenceCode }.toSet()

    if (sdsCodes.isNotEmpty() && schedule19ZACodes.intersect(sdsCodes).isNotEmpty()) {
      messages += listOf(ValidationMessage(ValidationCode.SDS_TORERA_EXCLUSION))
    }

    /**
     * Any SPOC sentences with a sentence date before 2022-06-28 must not include any offence codes
     * present within Schedule 19ZA
     */
    val spocCodes = sentenceAndOffences
      .filter {
        listOf(
          SentenceCalculationType.SEC236A.name,
          SentenceCalculationType.SOPC18.name,
          SentenceCalculationType.SOPC21.name,
        ).contains(it.sentenceCalculationType) && it.sentenceDate < LocalDate.parse("2022-06-28")
      }
      .map { it.offence.offenceCode }.toSet()

    if (spocCodes.isNotEmpty() && schedule19ZACodes.intersect(spocCodes).isNotEmpty()) {
      messages += listOf(ValidationMessage(ValidationCode.SPOC_TORERA_EXCLUSION))
    }

    return messages
  }

  companion object {
    private val BOOKING_ADJUSTMENTS_TO_VALIDATE =
      listOf(ADDITIONAL_DAYS_AWARDED, UNLAWFULLY_AT_LARGE, RESTORED_ADDITIONAL_DAYS_AWARDED)
    private val ADJUSTMENT_FUTURE_DATED_MAP = mapOf(
      ADDITIONAL_DAYS_AWARDED to ADJUSTMENT_FUTURE_DATED_ADA,
      UNLAWFULLY_AT_LARGE to ADJUSTMENT_FUTURE_DATED_UAL,
      RESTORED_ADDITIONAL_DAYS_AWARDED to ADJUSTMENT_FUTURE_DATED_RADA,
    )

    private val AFTER_97_BREACH_PROVISION_INVALID = LocalDate.of(2020, 12, 1)
    private const val TWELVE = 12L
    private val log = LoggerFactory.getLogger(this::class.java)
  }
}
