package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationRule
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceIdentificationTrack
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AdjustmentDuration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculableSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ConsecutiveSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offender
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ReleaseDateCalculationBreakdown
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.StandardDeterminateSentence
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Service
class TusedCalculator(val workingDayService: WorkingDayService) {
  fun doesTopUpSentenceExpiryDateApply(sentence: CalculableSentence, offender: Offender): Boolean {
    val oraCondition = when (sentence) {
      is StandardDeterminateSentence -> {
        sentence.isOraSentence()
      }

      is ConsecutiveSentence -> {
        sentence.hasOraSentences()
      }

      else -> {
        false
      }
    }

    val lapsoCondition = when (sentence) {
      is StandardDeterminateSentence -> {
        sentence.identificationTrack == SentenceIdentificationTrack.SDS_AFTER_CJA_LASPO
      }

      is ConsecutiveSentence -> {
        sentence.isMadeUpOfOnlyAfterCjaLaspoSentences()
      }

      else -> {
        false
      }
    }

    return oraCondition && lapsoCondition &&
      sentence.durationIsLessThanEqualTo(TWO, ChronoUnit.YEARS) &&
      sentence.getLengthInDays() > INT_ONE &&
      offender.getAgeOnDate(sentence.getHalfSentenceDate()) > INT_EIGHTEEN
  }

  fun calculateTused(sentenceCalculation: SentenceCalculation): LocalDate {
    val adjustedDays =
      getAdjustedDays(sentenceCalculation)
    val previousWorkingDay = workingDayService.previousWorkingDay(sentenceCalculation.unadjustedDeterminateReleaseDate)

    val ualAfterSentenceDateBeforeAdjustedDate = sentenceCalculation.getAdjustmentsAfterSentenceAtDateBeforeNonWorkingDayAdjustedReleaseDate(AdjustmentType.UNLAWFULLY_AT_LARGE, nonWorkingDayAdjustedDate = previousWorkingDay.date)
    val ualAfterNonWorkingDayAdjusted = sentenceCalculation.getAdjustmentsAfterNonWorkingDayAdjustedReleaseDate(AdjustmentType.UNLAWFULLY_AT_LARGE, nonWorkingDayAdjustedDate = previousWorkingDay.date)
    return if (sentenceCalculation.isImmediateRelease()) {
      // There may still be adjustments to consider here. If the immediate release occurred and then there was a recall,
      // Any UAL after the recall will need to be added.
      val adjustedDaysAfterRelease = sentenceCalculation.getTotalAddedDaysAfter(sentenceCalculation.sentence.sentencedAt)
      sentenceCalculation.sentence.sentencedAt
        .plusMonths(TWELVE)
        .plusDays(adjustedDaysAfterRelease.toLong())
    } else if (sentenceCalculation.sentence.isRecall()) {
      if (ualAfterSentenceDateBeforeAdjustedDate > 0 || ualAfterNonWorkingDayAdjusted > 0) {
        sentenceCalculation.unadjustedDeterminateReleaseDate
          .minusDays(sentenceCalculation.calculatedTotalDeductedDays.toLong())
          .plusMonths(TWELVE)
          .plusDays(sentenceCalculation.calculatedTotalAddedDays.toLong())
      } else {
        sentenceCalculation.unadjustedDeterminateReleaseDate
          .plusDays(adjustedDays.toLong())
          .plusMonths(TWELVE)
      }
    } else {
      sentenceCalculation.unadjustedDeterminateReleaseDate
        .plusDays(adjustedDays.toLong())
        .plusMonths(TWELVE)
    }
  }

  fun getCalculationBreakdown(sentenceCalculation: SentenceCalculation): ReleaseDateCalculationBreakdown {
    return ReleaseDateCalculationBreakdown(
      rules = setOf(CalculationRule.TUSED_LICENCE_PERIOD_LT_1Y) + if (sentenceCalculation.isImmediateRelease()) setOf(CalculationRule.IMMEDIATE_RELEASE) else emptySet(),
      rulesWithExtraAdjustments = mapOf(
        CalculationRule.TUSED_LICENCE_PERIOD_LT_1Y to AdjustmentDuration(
          TWELVE.toInt(),
          ChronoUnit.MONTHS
        )
      ),
      adjustedDays = getAdjustedDays(sentenceCalculation),
      releaseDate = sentenceCalculation.topUpSupervisionDate!!,
      unadjustedDate = sentenceCalculation.unadjustedDeterminateReleaseDate,
    )
  }

  private fun getAdjustedDays(sentenceCalculation: SentenceCalculation): Int {
    return sentenceCalculation.calculatedTotalAddedDays.minus(sentenceCalculation.calculatedTotalDeductedDays)
  }

  companion object {
    private const val INT_EIGHTEEN = 18
    private const val INT_ONE = 1
    private const val TWO = 2L
    private const val TWELVE = 12L
  }
}