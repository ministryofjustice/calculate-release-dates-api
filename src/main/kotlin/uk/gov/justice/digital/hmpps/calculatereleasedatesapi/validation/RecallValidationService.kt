package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.adjustmentsapi.model.AdjustmentDto
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.FeatureToggles
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.SDS40TrancheConfiguration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AbstractSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculableSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationOutput
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ConsecutiveSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RecallType.FIXED_TERM_RECALL_14
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RecallType.FIXED_TERM_RECALL_28
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.StandardDeterminateSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.CalculationSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.FixedTermRecallDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAdjustmentType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAndOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType.Companion.from
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.ImportantDates.FTR_48_COMMENCEMENT_DATE
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.findClosest12MonthOrGreaterSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.findClosestUnder12MonthSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.isAfterOrEqualTo
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.ChronoUnit.MONTHS
import kotlin.math.abs

@Service
class RecallValidationService(
  private val trancheConfiguration: SDS40TrancheConfiguration,
  private val validationUtilities: ValidationUtilities,
  private val featureToggles: FeatureToggles,
) {

  internal fun validateFixedTermRecall(sourceData: CalculationSourceData): List<ValidationMessage> {
    val ftrDetails = sourceData.fixedTermRecallDetails ?: return emptyList()
    val (recallLength, has14DayFTRSentence, has28DayFTRSentence) = getFtrValidationDetails(
      ftrDetails,
      sourceData.sentenceAndOffences,
    )

    return when {
      sourceData.returnToCustodyDate == null -> listOf(ValidationMessage(ValidationCode.FTR_NO_RETURN_TO_CUSTODY_DATE))
      has14DayFTRSentence && has28DayFTRSentence -> listOf(ValidationMessage(ValidationCode.FTR_SENTENCES_CONFLICT_WITH_EACH_OTHER))
      has14DayFTRSentence && recallLength == 28 -> listOf(ValidationMessage(ValidationCode.FTR_TYPE_14_DAYS_BUT_LENGTH_IS_28))
      has28DayFTRSentence && recallLength == 14 -> listOf(ValidationMessage(ValidationCode.FTR_TYPE_28_DAYS_BUT_LENGTH_IS_14))
      else -> emptyList()
    }
  }

  data class RemandPeriodToValidate(
    val sentenceSequence: Int,
    val fromDate: LocalDate,
    val toDate: LocalDate,
  )

  internal fun validateRemandPeriodsAgainstSentenceDates(sourceData: CalculationSourceData): List<ValidationMessage> {
    val remandPeriods = sourceData.bookingAndSentenceAdjustments.fold(
      { adjustments ->
        adjustments.sentenceAdjustments
          .filter {
            it.type in setOf(
              SentenceAdjustmentType.REMAND,
              SentenceAdjustmentType.RECALL_SENTENCE_REMAND,
            ) &&
              it.fromDate != null &&
              it.toDate != null
          }.map {
            RemandPeriodToValidate(
              it.sentenceSequence,
              it.fromDate!!,
              it.toDate!!,
            )
          }
      },
      { adjustments ->
        adjustments
          .filter {
            it.adjustmentType == AdjustmentDto.AdjustmentType.REMAND && it.fromDate != null && it.toDate != null
          }.map {
            RemandPeriodToValidate(
              it.sentenceSequence!!,
              it.fromDate!!,
              it.toDate!!,
            )
          }
      },
    )

    val validationMessages = mutableListOf<ValidationMessage>()

    val sentenceMap = sourceData.sentenceAndOffences.associateBy { it.sentenceSequence }

    remandPeriods.forEach { remandPeriod ->
      val sentence = sentenceMap[remandPeriod.sentenceSequence]
      if (sentence != null) {
        val sentenceDate = sentence.sentenceDate

        val areRemandDatesAfterSentenceDate = remandPeriod.fromDate.isAfterOrEqualTo(sentenceDate) ||
          remandPeriod.toDate.isAfterOrEqualTo(sentenceDate)

        if (areRemandDatesAfterSentenceDate) {
          validationMessages.add(
            ValidationMessage(
              ValidationCode.REMAND_ON_OR_AFTER_SENTENCE_DATE,
              validationUtilities.getCaseSeqAndLineSeq(sentence),
            ),
          )
        }
      }
    }

    return validationMessages
  }

  internal fun getFtrValidationDetails(
    ftrDetails: FixedTermRecallDetails,
    sentencesAndOffences: List<SentenceAndOffence>,
  ): Triple<Int, Boolean, Boolean> {
    val recallLength = ftrDetails.recallLength
    val bookingsSentenceTypes = sentencesAndOffences.map { from(it.sentenceCalculationType) }
    val has14DayFTRSentence = bookingsSentenceTypes.any { it == SentenceCalculationType.FTR_14_ORA }
    val has28DayFTRSentence = SentenceCalculationType.entries.any { it.recallType?.isFixedTermRecall ?: false && it != SentenceCalculationType.FTR_14_ORA && bookingsSentenceTypes.contains(it) }
    return Triple(recallLength, has14DayFTRSentence, has28DayFTRSentence)
  }

  internal fun validateUnsupportedRecallTypes(calculationOutput: CalculationOutput, booking: Booking): List<ValidationMessage> = if (hasUnsupportedRecallType(calculationOutput, booking)) {
    listOf(ValidationMessage(ValidationCode.UNSUPPORTED_SDS40_RECALL_SENTENCE_TYPE))
  } else {
    emptyList()
  }

  private fun hasUnsupportedRecallType(calculationOutput: CalculationOutput, booking: Booking): Boolean {
    if (!featureToggles.externalMovementsEnabled) {
      return calculationOutput.sentences.any { sentence ->
        val hasTusedReleaseDateType = sentence.releaseDateTypes.contains(ReleaseDateType.TUSED)
        val isDeterminateOrConsecutiveSentence = sentence.sentenceParts().any { it is StandardDeterminateSentence }
        val sentencedBeforeCommencement = sentence.sentencedAt.isBefore(trancheConfiguration.trancheOneCommencementDate)
        val adjustedReleaseDateAfterOrEqualCommencement =
          sentence.sentenceCalculation.adjustedHistoricDeterminateReleaseDate
            .isAfterOrEqualTo(trancheConfiguration.trancheOneCommencementDate)

        val result = hasTusedReleaseDateType &&
          isDeterminateOrConsecutiveSentence &&
          sentence.isRecall() &&
          sentencedBeforeCommencement &&
          adjustedReleaseDateAfterOrEqualCommencement

        if (result) {
          log.info(
            "Unsupported recall type found for sentence ${
              sentence.sentenceParts().map { (it as AbstractSentence).identifier }
            } in booking for ${booking.offender.reference}.",
          )
        }
        return@any result
      }
    }
    // SDS40 recalls are supported with external movements.
    return false
  }

  internal fun validateFixedTermRecall(booking: Booking): List<ValidationMessage> {
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

    // ignore mixed durations if revised rules are not enabled
    val ftr14NoUnder12MonthDuration =
      ftr14Sentences.none { it.durationIsLessThan(TWELVE, MONTHS) }

    val ftrSentenceExistsInConsecutiveChain = ftrSentences.any { it.consecutiveSentenceUUIDs.isNotEmpty() } ||
      booking.sentences.any {
        it.consecutiveSentenceUUIDs.toSet().intersect(ftrSentencesUuids.toSet()).isNotEmpty()
      }

    if (
      ftr14NoUnder12MonthDuration &&
      !maxFtrSentenceIsLessThan12Months &&
      recallLength == 14
    ) {
      validationMessages += ValidationMessage(ValidationCode.FTR_14_DAYS_SENTENCE_GE_12_MONTHS)
    }

    if (maxFtrSentenceIsLessThan12Months && recallLength == 28 && !ftrSentenceExistsInConsecutiveChain) {
      validationMessages += ValidationMessage(ValidationCode.FTR_28_DAYS_SENTENCE_LT_12_MONTHS)
    }

    if (ftr28Sentences.isNotEmpty() && maxFtrSentenceIsLessThan12Months && !ftrSentenceExistsInConsecutiveChain) {
      validationMessages += ValidationMessage(ValidationCode.FTR_TYPE_28_DAYS_SENTENCE_LT_12_MONTHS)
    }

    if (
      ftr14NoUnder12MonthDuration &&
      ftr14Sentences.any { it.durationIsGreaterThanOrEqualTo(TWELVE, MONTHS) }
    ) {
      validationMessages += ValidationMessage(ValidationCode.FTR_TYPE_14_DAYS_SENTENCE_GE_12_MONTHS)
    }

    return validationMessages
  }

  internal fun validateFixedTermRecallAfterCalc(calculationOutput: CalculationOutput, booking: Booking): List<ValidationMessage> {
    val messages = mutableListOf<ValidationMessage>()
    val ftrDetails = booking.fixedTermRecallDetails ?: return messages

    val ftrSentences = calculationOutput.sentences.filter {
      it.isRecall() && it.sentenceCalculation.adjustedExpiryDate.isAfterOrEqualTo(booking.fixedTermRecallDetails.returnToCustodyDate)
    }

    val hasOverTwelveMonthSentence = ftrSentences.any { it.durationIsGreaterThanOrEqualTo(TWELVE, MONTHS) }
    val hasUnderTwelveMonthSentence = ftrSentences.any { it.durationIsLessThan(TWELVE, MONTHS) }

    return if (hasUnderTwelveMonthSentence && hasOverTwelveMonthSentence) {
      validateMixedDurations(ftrSentences, booking, calculationOutput)
    } else {
      validateSingleDurationRecalls(calculationOutput, ftrDetails, hasUnderTwelveMonthSentence, hasOverTwelveMonthSentence)
    }
  }

  internal fun validateSingleDurationRecalls(
    calculationOutput: CalculationOutput,
    ftrDetails: FixedTermRecallDetails,
    hasUnderTwelveMonthSentence: Boolean,
    hasOverTwelveMonthSentence: Boolean,
  ): MutableList<ValidationMessage> {
    val messages = mutableListOf<ValidationMessage>()
    val recallLength = ftrDetails.recallLength
    val consecutiveSentences = calculationOutput.sentences.filterIsInstance<ConsecutiveSentence>()

    consecutiveSentences.forEach {
      if (!hasUnderTwelveMonthSentence && it.recallType == FIXED_TERM_RECALL_14 || it.recallType == FIXED_TERM_RECALL_28) {
        if (recallLength == 14 && it.durationIsGreaterThanOrEqualTo(TWELVE, MONTHS)) {
          messages += ValidationMessage(ValidationCode.FTR_14_DAYS_AGGREGATE_GE_12_MONTHS)
        }
      }
    }

    consecutiveSentences.forEach {
      if (!hasOverTwelveMonthSentence && (it.recallType == FIXED_TERM_RECALL_14 || it.recallType == FIXED_TERM_RECALL_28)) {
        if (recallLength == 28 && it.durationIsLessThan(TWELVE, MONTHS)) {
          messages += ValidationMessage(
            ValidationCode.FTR_28_DAYS_AGGREGATE_LT_12_MONTHS,
          )
        }
      }
    }

    consecutiveSentences.forEach {
      if (!hasOverTwelveMonthSentence && it.recallType == FIXED_TERM_RECALL_28 && it.durationIsLessThan(TWELVE, MONTHS)) {
        messages += ValidationMessage(ValidationCode.FTR_TYPE_28_DAYS_AGGREGATE_LT_12_MONTHS)
      }
    }

    consecutiveSentences.forEach {
      if (!hasUnderTwelveMonthSentence && it.recallType == FIXED_TERM_RECALL_14 && it.durationIsGreaterThanOrEqualTo(TWELVE, MONTHS)) {
        messages += ValidationMessage(ValidationCode.FTR_TYPE_14_DAYS_AGGREGATE_GE_12_MONTHS)
      }
    }
    return messages
  }

  internal fun validateFtrFortyOverlap(sentences: List<CalculableSentence>): List<ValidationMessage> {
    val possibleSentences = sentences.filter { it.recallType == FIXED_TERM_RECALL_28 && it.sentencedAt.isBefore(FTR_48_COMMENCEMENT_DATE) }
    val allSentencesLessThan12Months = possibleSentences.all { it.durationIsLessThan(12, MONTHS) }
    val anySentenceEqualOrOver48Months = possibleSentences.any { it.durationIsGreaterThanOrEqualTo(48, MONTHS) }
    if (featureToggles.ftr48ManualJourney && possibleSentences.isNotEmpty() && !allSentencesLessThan12Months && !anySentenceEqualOrOver48Months) {
      return listOf(ValidationMessage(ValidationCode.FTR_TYPE_48_DAYS_OVERLAPPING_SENTENCE))
    }
    return emptyList()
  }

  private fun validateMixedDurations(
    ftrSentences: List<CalculableSentence>,
    booking: Booking,
    calculationOutput: CalculationOutput,
  ): List<ValidationMessage> {
    val returnToCustodyDate = booking.returnToCustodyDate ?: return emptyList()
    val calculatedSled = calculationOutput.calculationResult.dates[ReleaseDateType.SLED]
    val sledProducingSentence = ftrSentences.find { it.sentenceCalculation.adjustedExpiryDate == calculatedSled } ?: return emptyList()
    val sledSentenceUnder12Months = sledProducingSentence.durationIsLessThan(12, MONTHS)
    val sledSentenceOver12Months = !sledSentenceUnder12Months

    val closestUnder12MonthSentence = ftrSentences.findClosestUnder12MonthSentence(sledProducingSentence, returnToCustodyDate)
    val closest12MonthSentence = ftrSentences.findClosest12MonthOrGreaterSentence(sledProducingSentence, returnToCustodyDate)

    return when {
      sledSentenceOver12Months &&
        closestUnder12MonthSentence !== null &&
        closestUnder12MonthSentence.recallType == FIXED_TERM_RECALL_14 &&
        abs(ChronoUnit.DAYS.between(closestUnder12MonthSentence.sentenceCalculation.adjustedExpiryDate, calculatedSled)) >= 14 -> {
        listOf(ValidationMessage(ValidationCode.FTR_TYPE_14_DAYS_SENTENCE_GT_12_MONTHS))
      }

      sledSentenceUnder12Months &&
        closest12MonthSentence != null &&
        sledProducingSentence.recallType == FIXED_TERM_RECALL_14 &&
        abs(ChronoUnit.DAYS.between(returnToCustodyDate, closest12MonthSentence.sentenceCalculation.adjustedExpiryDate)) >= 14 -> {
        listOf(ValidationMessage(ValidationCode.FTR_TYPE_14_DAYS_SENTENCE_GAP_GT_14_DAYS))
      }

      closest12MonthSentence !== null &&
        sledProducingSentence.recallType == FIXED_TERM_RECALL_28 &&
        sledSentenceUnder12Months &&
        abs(ChronoUnit.DAYS.between(returnToCustodyDate, closest12MonthSentence.sentenceCalculation.adjustedExpiryDate)) <= 14 -> {
        listOf(ValidationMessage(ValidationCode.FTR_TYPE_28_DAYS_SENTENCE_GAP_LT_14_DAYS))
      }

      else -> emptyList()
    }
  }

  companion object {
    private const val TWELVE = 12L
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}
