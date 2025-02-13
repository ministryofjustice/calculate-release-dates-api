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

  private data class MixedDurationRecall(
    val sledSentence: CalculableSentence,
    val relatedSentences: List<CalculableSentence>,
    val returnToCustodyDate: LocalDate,
  )

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

    val ftr14NoUnder12MonthDuration = if (featureToggles.revisedFixedTermRecallsRules) ftr14Sentences.none { it.durationIsLessThan(TWELVE, MONTHS) } else true

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
    val recallLength = ftrDetails.recallLength
    val consecutiveSentences = calculationOutput.sentences.filterIsInstance<ConsecutiveSentence>()

    consecutiveSentences.forEach {
      if (it.recallType == FIXED_TERM_RECALL_14 || it.recallType == FIXED_TERM_RECALL_28) {
        if (recallLength == 14 && it.durationIsGreaterThanOrEqualTo(TWELVE, MONTHS)) {
          messages += ValidationMessage(ValidationCode.FTR_14_DAYS_AGGREGATE_GE_12_MONTHS)
        }
      }
    }

    consecutiveSentences.forEach {
      if (it.recallType == FIXED_TERM_RECALL_14 || it.recallType == FIXED_TERM_RECALL_28) {
        if (recallLength == 28 && it.durationIsLessThan(TWELVE, MONTHS)) {
          messages += ValidationMessage(
            ValidationCode.FTR_28_DAYS_AGGREGATE_LT_12_MONTHS,
          )
        }
      }
    }

    consecutiveSentences.forEach {
      if (it.recallType == FIXED_TERM_RECALL_28 && it.durationIsLessThan(TWELVE, MONTHS)) {
        messages += ValidationMessage(ValidationCode.FTR_TYPE_28_DAYS_AGGREGATE_LT_12_MONTHS)
      }
    }

    consecutiveSentences.forEach {
      if (it.recallType == FIXED_TERM_RECALL_14 && it.durationIsGreaterThanOrEqualTo(TWELVE, MONTHS)) {
        messages += ValidationMessage(ValidationCode.FTR_TYPE_14_DAYS_AGGREGATE_GE_12_MONTHS)
      }
    }
    return messages
  }

  /**
   * Validates recalls with sentences under and over 12 months for the following violations:
   *
   * 1. The current sentence is a 14-day recall and its duration is 12 months or greater.
   * 2. A previous 14-day recall sentence exists and the gap between the return to custody date and the expiry date of the previous sentence is greater than 14 days.
   * 3. A previous 28-day recall sentence exists and the gap between the return to custody date and the expiry date of the previous sentence is less than or equal to 14 days.
   *
   * @param calculationOutput
   * @param booking
   * @return A list of validation messages.
   */
  internal fun validateMixedDurations(
    calculationOutput: CalculationOutput,
    booking: Booking,
  ): List<ValidationMessage> {
    val mixedDurationRecall = getMixedDurationSled(calculationOutput, booking) ?: return emptyList()
    val previous14DayRecallSentence = mixedDurationRecall.relatedSentences.lastOrNull { it.recallType == FIXED_TERM_RECALL_14 }
    val previous28DayRecallSentence = mixedDurationRecall.relatedSentences.lastOrNull { it.recallType == FIXED_TERM_RECALL_28 }

    when {
      mixedDurationRecall.sledSentence.recallType == FIXED_TERM_RECALL_14 &&
        mixedDurationRecall.sledSentence.durationIsGreaterThanOrEqualTo(12, MONTHS) -> {
        return listOf(ValidationMessage(ValidationCode.FTR_TYPE_14_DAYS_SENTENCE_GT_12_MONTHS))
      }

      previous14DayRecallSentence != null &&
        mixedDurationRecall.sledSentence.durationIsLessThan(12, MONTHS) &&
        mixedDurationRecall.sledSentence.recallType == FIXED_TERM_RECALL_14 &&
        abs(
          ChronoUnit.DAYS.between(
            mixedDurationRecall.returnToCustodyDate,
            previous14DayRecallSentence.sentenceCalculation.expiryDate,
          ),
        ) >= 14 -> {
        return listOf(ValidationMessage(ValidationCode.FTR_TYPE_14_DAYS_SENTENCE_GAP_GT_14_DAYS))
      }

      previous28DayRecallSentence != null &&
        mixedDurationRecall.sledSentence.durationIsLessThan(12, MONTHS) &&
        mixedDurationRecall.sledSentence.recallType == FIXED_TERM_RECALL_28 &&
        abs(
          ChronoUnit.DAYS.between(
            mixedDurationRecall.returnToCustodyDate,
            previous28DayRecallSentence.sentenceCalculation.expiryDate,
          ),
        ) <= 14 -> {
        return listOf(ValidationMessage(ValidationCode.FTR_TYPE_28_DAYS_SENTENCE_GAP_LT_14_DAYS))
      }
    }

    return emptyList()
  }

  /**
   * Sentence matching the SLED date must exist
   * Return to custody date must exist for valid recall
   * Recall sentences must contain under and over 12 month durations
   */
  private fun getMixedDurationSled(calculationOutput: CalculationOutput, booking: Booking): MixedDurationRecall? {
    val sled = calculationOutput.calculationResult.dates[ReleaseDateType.SLED] ?: return null
    val returnToCustodyDate = booking.returnToCustodyDate ?: return null

    val fixedTermRecallSentences = calculationOutput.sentences.filter {
      it.recallType == FIXED_TERM_RECALL_14 || it.recallType == FIXED_TERM_RECALL_28
    }.sortedBy { it.sentencedAt }

    if (
      fixedTermRecallSentences.none { it.durationIsLessThan(12, MONTHS) } ||
      fixedTermRecallSentences.none { it.durationIsGreaterThanOrEqualTo(12, MONTHS) }
    ) {
      return null
    }

    val sledSentence =
      fixedTermRecallSentences.lastOrNull { it.sentenceCalculation.expiryDate == sled } ?: return null

    return MixedDurationRecall(
      relatedSentences = fixedTermRecallSentences.filterNot { it == sledSentence },
      returnToCustodyDate = returnToCustodyDate,
      sledSentence = sledSentence,
    )
  }

  companion object {
    private const val TWELVE = 12L
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}
