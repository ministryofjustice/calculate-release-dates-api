package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
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
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.FixedTermRecallDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonApiSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAdjustmentType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAndOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType.Companion.from
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.isAfterOrEqualTo
import java.time.temporal.ChronoUnit
import java.time.temporal.ChronoUnit.MONTHS
import kotlin.math.abs

@Service
class RecallValidationService(
  private val trancheConfiguration: SDS40TrancheConfiguration,
  private val validationUtilities: ValidationUtilities,
  private val featureToggles: FeatureToggles,
) {

  internal fun validateFixedTermRecall(sourceData: PrisonApiSourceData): List<ValidationMessage> {
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

  internal fun validateRemandPeriodsAgainstSentenceDates(sourceData: PrisonApiSourceData): List<ValidationMessage> {
    val remandPeriods = sourceData.bookingAndSentenceAdjustments.sentenceAdjustments
      .filter {
        it.type in setOf(
          SentenceAdjustmentType.REMAND,
          SentenceAdjustmentType.RECALL_SENTENCE_REMAND,
        )
      }

    val validationMessages = mutableListOf<ValidationMessage>()

    val sentenceMap = sourceData.sentenceAndOffences.associateBy { it.sentenceSequence }

    remandPeriods.forEach { remandPeriod ->
      val sentence = sentenceMap[remandPeriod.sentenceSequence]
      if (sentence != null) {
        val sentenceDate = sentence.sentenceDate

        val areRemandDatesAfterSentenceDate = (remandPeriod.fromDate?.isAfterOrEqualTo(sentenceDate) ?: false) ||
          (remandPeriod.toDate?.isAfterOrEqualTo(sentenceDate) ?: false)

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

  internal fun validateUnsupportedRecallTypes(calculationOutput: CalculationOutput, booking: Booking): List<ValidationMessage> {
    return if (hasUnsupportedRecallType(calculationOutput, booking)) {
      listOf(ValidationMessage(ValidationCode.UNSUPPORTED_SDS40_RECALL_SENTENCE_TYPE))
    } else {
      emptyList()
    }
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
    val ftr14NoUnder12MonthDuration = if (featureToggles.revisedFixedTermRecallsRules) {
      ftr14Sentences.none { it.durationIsLessThan(TWELVE, MONTHS) }
    } else {
      true
    }

    val ftrSentenceExistsInConsecutiveChain = ftrSentences.any { it.consecutiveSentenceUUIDs.isNotEmpty() } ||
      booking.sentences.any {
        it.consecutiveSentenceUUIDs.toSet().intersect(ftrSentencesUuids.toSet()).isNotEmpty()
      }

    if (
      ftr14NoUnder12MonthDuration &&
      !maxFtrSentenceIsLessThan12Months && recallLength == 14
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

    // ignore mixed durations if revised rules are not enabled
    if (!featureToggles.revisedFixedTermRecallsRules) {
      return validateSingleDurationRecalls(calculationOutput, ftrDetails, false, false)
    }

    val ftrSentences = calculationOutput.sentences.filter {
      it.isRecall() && it.sentenceCalculation.adjustedExpiryDate.isAfterOrEqualTo(booking.fixedTermRecallDetails.returnToCustodyDate)
    }

    val hasOverTwelveMonthSentence = ftrSentences.any { it.durationIsGreaterThanOrEqualTo(TWELVE, MONTHS) }
    val hasUnderTwelveMonthSentence = ftrSentences.any { it.durationIsLessThan(TWELVE, MONTHS) }

    return if (hasUnderTwelveMonthSentence && hasOverTwelveMonthSentence) {
      validateMixedDurations(ftrSentences, booking)
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

  private fun validateMixedDurations(
    ftrSentences: List<CalculableSentence>,
    booking: Booking,
  ): List<ValidationMessage> {
    val returnToCustodyDate = booking.returnToCustodyDate ?: return emptyList()
    val latestSled = ftrSentences.maxBy { it.sentenceCalculation.adjustedExpiryDate }

    val sentencesOver12Months = ftrSentences.filter {
      it !== latestSled && it.durationIsGreaterThanOrEqualTo(12, MONTHS)
    }

    val latestSledUnderTwelveMonths = latestSled.durationIsLessThan(TWELVE, MONTHS)
    val latestSledOverTwelveMonths = !latestSledUnderTwelveMonths

    return when {
      latestSledOverTwelveMonths && latestSled.recallType == FIXED_TERM_RECALL_14 -> {
        listOf(ValidationMessage(ValidationCode.FTR_TYPE_14_DAYS_SENTENCE_GT_12_MONTHS))
      }

      latestSledUnderTwelveMonths && latestSled.recallType == FIXED_TERM_RECALL_14 && sentencesOver12Months.none {
        abs(ChronoUnit.DAYS.between(returnToCustodyDate, it.sentenceCalculation.adjustedExpiryDate)) <= 14
      } -> {
        listOf(ValidationMessage(ValidationCode.FTR_TYPE_14_DAYS_SENTENCE_GAP_GT_14_DAYS))
      }

      latestSledUnderTwelveMonths && latestSled.recallType == FIXED_TERM_RECALL_28 && sentencesOver12Months.any {
        abs(ChronoUnit.DAYS.between(returnToCustodyDate, it.sentenceCalculation.adjustedExpiryDate)) <= 14
      } -> {
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
