package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.validator

import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculableSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationOutput
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ConsecutiveSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RecallType.FIXED_TERM_RECALL_14
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RecallType.FIXED_TERM_RECALL_28
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.FixedTermRecallDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.findClosest12MonthOrGreaterSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.findClosestUnder12MonthSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.isAfterOrEqualTo
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationMessage
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationOrder
import java.time.temporal.ChronoUnit
import java.time.temporal.ChronoUnit.MONTHS
import kotlin.math.abs

@Component
class FixedTermRecallAfterCalculationValidator : PostCalculationValidator {

  override fun validate(calculationOutput: CalculationOutput, booking: Booking): List<ValidationMessage> {
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
      validateSingleDurationRecalls(
        calculationOutput,
        ftrDetails,
        hasUnderTwelveMonthSentence,
        hasOverTwelveMonthSentence,
      )
    }
  }
  private fun validateSingleDurationRecalls(
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
      if (!hasOverTwelveMonthSentence &&
        it.recallType == FIXED_TERM_RECALL_28 &&
        it.durationIsLessThan(
          TWELVE,
          MONTHS,
        )
      ) {
        messages += ValidationMessage(ValidationCode.FTR_TYPE_28_DAYS_AGGREGATE_LT_12_MONTHS)
      }
    }

    consecutiveSentences.forEach {
      if (!hasUnderTwelveMonthSentence &&
        it.recallType == FIXED_TERM_RECALL_14 &&
        it.durationIsGreaterThanOrEqualTo(
          TWELVE,
          MONTHS,
        )
      ) {
        messages += ValidationMessage(ValidationCode.FTR_TYPE_14_DAYS_AGGREGATE_GE_12_MONTHS)
      }
    }
    return messages
  }

  private fun validateMixedDurations(
    ftrSentences: List<CalculableSentence>,
    booking: Booking,
    calculationOutput: CalculationOutput,
  ): List<ValidationMessage> {
    val returnToCustodyDate = booking.returnToCustodyDate ?: return emptyList()
    val calculatedSled = calculationOutput.calculationResult.dates[ReleaseDateType.SLED]
    val sledProducingSentence =
      ftrSentences.find { it.sentenceCalculation.adjustedExpiryDate == calculatedSled } ?: return emptyList()
    val sledSentenceUnder12Months = sledProducingSentence.durationIsLessThan(12, MONTHS)
    val sledSentenceOver12Months = !sledSentenceUnder12Months

    val closestUnder12MonthSentence =
      ftrSentences.findClosestUnder12MonthSentence(sledProducingSentence, returnToCustodyDate)
    val closest12MonthSentence =
      ftrSentences.findClosest12MonthOrGreaterSentence(sledProducingSentence, returnToCustodyDate)

    return when {
      sledSentenceOver12Months &&
        closestUnder12MonthSentence !== null &&
        closestUnder12MonthSentence.recallType == FIXED_TERM_RECALL_14 &&
        abs(
          ChronoUnit.DAYS.between(
            closestUnder12MonthSentence.sentenceCalculation.adjustedExpiryDate,
            calculatedSled,
          ),
        ) >= 14 -> {
        listOf(ValidationMessage(ValidationCode.FTR_TYPE_14_DAYS_SENTENCE_GT_12_MONTHS))
      }

      sledSentenceUnder12Months &&
        closest12MonthSentence != null &&
        sledProducingSentence.recallType == FIXED_TERM_RECALL_14 &&
        abs(
          ChronoUnit.DAYS.between(
            returnToCustodyDate,
            closest12MonthSentence.sentenceCalculation.adjustedExpiryDate,
          ),
        ) >= 14 -> {
        listOf(ValidationMessage(ValidationCode.FTR_TYPE_14_DAYS_SENTENCE_GAP_GT_14_DAYS))
      }

      closest12MonthSentence !== null &&
        sledProducingSentence.recallType == FIXED_TERM_RECALL_28 &&
        sledSentenceUnder12Months &&
        abs(
          ChronoUnit.DAYS.between(
            returnToCustodyDate,
            closest12MonthSentence.sentenceCalculation.adjustedExpiryDate,
          ),
        ) <= 14 -> {
        listOf(ValidationMessage(ValidationCode.FTR_TYPE_28_DAYS_SENTENCE_GAP_LT_14_DAYS))
      }

      else -> emptyList()
    }
  }

  override fun validationOrder() = ValidationOrder.INVALID

  companion object {
    private const val TWELVE = 12L
  }
}
