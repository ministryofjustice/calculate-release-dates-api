package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.FeatureToggles
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationRule
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.TUSED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AdjustmentDuration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculableSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ConsecutiveSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offender
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ReleaseDateCalculationBreakdown
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.StandardDeterminateSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.ImportantDates.POST_SUPERVISION_REPEAL_DATE
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.isAfterOrEqualTo
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Service
class TusedCalculator(private val featureToggles: FeatureToggles) {
  fun sentenceIsEligibleForTused(sentence: CalculableSentence, offender: Offender): Boolean {
    if (featureToggles.validatePostRecallRepealDate &&
      sentence.sentenceParts()
        .any { it.sentencedAt.isAfterOrEqualTo(POST_SUPERVISION_REPEAL_DATE) }
    ) {
      return false
    }

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
        sentence.isAfterCJAAndLASPO()
      }

      is ConsecutiveSentence -> {
        sentence.isMadeUpOfOnlyAfterCjaLaspoSentences()
      }

      else -> {
        false
      }
    }

    return oraCondition &&
      lapsoCondition &&
      sentence.durationIsLessThan(TWO, ChronoUnit.YEARS) &&
      sentence.getLengthInDays() > INT_ONE &&
      offender.getAgeOnDate(sentence.getHalfSentenceDate()) > INT_EIGHTEEN
  }

  fun sentenceMatchesTusedCriteria(sentence: CalculableSentence, offender: Offender): Boolean {
    if (!sentence.isCalculationInitialised()) return false
    val sentenceCalculation = sentence.sentenceCalculation
    return sentenceCalculation.numberOfDaysToSentenceExpiryDate - sentenceCalculation.numberOfDaysToDeterminateReleaseDate < YEAR_IN_DAYS &&
      sentence.releaseDateTypes.contains(TUSED) &&
      offender.getAgeOnDate(sentence.sentenceCalculation.releaseDateWithoutAwarded) >= 18
  }

  fun calculateTused(sentenceCalculation: SentenceCalculation): LocalDate? {
    val tused = getInitialTused(sentenceCalculation)
    return if (featureToggles.validatePostRecallRepealDate) {
      amendTusedInlineWithPostSupervisionRepeal(
        sentenceCalculation,
        tused,
      )
    } else {
      tused
    }
  }

  fun getInitialTused(sentenceCalculation: SentenceCalculation): LocalDate = if (sentenceCalculation.isImmediateRelease()) {
    // There may still be adjustments to consider here. If the immediate release occurred and then there was a recall,
    // Any UAL after the recall will need to be added.
    val adjustedDaysAfterRelease = sentenceCalculation.adjustments.ualAfterDeterminateRelease
    sentenceCalculation.sentence.sentencedAt
      .plusMonths(TWELVE)
      .plusDays(adjustedDaysAfterRelease)
  } else {
    sentenceCalculation.unadjustedReleaseDate.unadjustedDeterminateReleaseDate
      .plusDays(sentenceCalculation.adjustments.adjustmentsForInitialReleaseWithoutAwarded())
      .plusMonths(TWELVE)
      .plusDays(sentenceCalculation.adjustments.ualAfterDeterminateRelease)
  }

  fun getCalculationBreakdown(sentenceCalculation: SentenceCalculation): ReleaseDateCalculationBreakdown = ReleaseDateCalculationBreakdown(
    rules = setOf(CalculationRule.TUSED_LICENCE_PERIOD_LT_1Y) + if (sentenceCalculation.isImmediateRelease()) setOf(CalculationRule.IMMEDIATE_RELEASE) else emptySet(),
    rulesWithExtraAdjustments = mapOf(
      CalculationRule.TUSED_LICENCE_PERIOD_LT_1Y to AdjustmentDuration(
        TWELVE,
        ChronoUnit.MONTHS,
      ),
    ),
    adjustedDays = getAdjustedDays(sentenceCalculation),
    releaseDate = sentenceCalculation.topUpSupervisionDate!!,
    unadjustedDate = sentenceCalculation.unadjustedDeterminateReleaseDate,
  )

  fun getCalculationBreakdownForBotus(sentenceCalculation: SentenceCalculation): ReleaseDateCalculationBreakdown = ReleaseDateCalculationBreakdown(
    rules = setOf(CalculationRule.BOTUS_LATEST_TUSED_USED) + if (sentenceCalculation.isImmediateRelease()) setOf(CalculationRule.IMMEDIATE_RELEASE) else emptySet(),
    adjustedDays = getAdjustedDays(sentenceCalculation),
    releaseDate = sentenceCalculation.topUpSupervisionDate!!,
    unadjustedDate = sentenceCalculation.unadjustedDeterminateReleaseDate,
  )

  fun amendTusedInlineWithPostSupervisionRepeal(sentenceCalculation: SentenceCalculation, tused: LocalDate): LocalDate? {
    val sentence = sentenceCalculation.sentence

    if (sentence.sentenceParts().any { it.sentencedAt.isAfterOrEqualTo(POST_SUPERVISION_REPEAL_DATE) }) {
      return null
    }

    val calc = sentence.sentenceCalculation
    val tusedAfterRepeal = tused.isAfterOrEqualTo(POST_SUPERVISION_REPEAL_DATE)
    val areAllSentencesImposedBeforeRepealDate = sentence.sentenceParts().all { sentencePart ->
      sentencePart.sentencedAt.isBefore(POST_SUPERVISION_REPEAL_DATE)
    }

    if (tusedAfterRepeal && areAllSentencesImposedBeforeRepealDate) {
      val tusedFallsAfterRepeal = calc.licenceExpiryDate?.isAfter(POST_SUPERVISION_REPEAL_DATE) == true ||
        calc.adjustedExpiryDate.isAfter(POST_SUPERVISION_REPEAL_DATE)

      return if (tusedFallsAfterRepeal) null else return POST_SUPERVISION_REPEAL_DATE.minusDays(1)
    }

    return if (areAllSentencesImposedBeforeRepealDate) tused else null
  }

  private fun getAdjustedDays(sentenceCalculation: SentenceCalculation): Long = sentenceCalculation.adjustments.adjustmentsForInitialReleaseWithoutAwarded()

  companion object {
    private const val INT_EIGHTEEN = 18
    private const val INT_ONE = 1
    private const val TWO = 2L
    private const val TWELVE = 12L
    private const val YEAR_IN_DAYS = 365
  }
}
