package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.FeatureToggles
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.ARD
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.CRD
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.NPD
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.PRRD
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.SLED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculableSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RecallType.FIXED_TERM_RECALL_14
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RecallType.FIXED_TERM_RECALL_28
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.isAfterOrEqualTo
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.ChronoUnit.MONTHS
import kotlin.math.abs

@Service
class FixedTermRecallsService(private val featureToggles: FeatureToggles) {

  fun calculatePRRD(
    sentences: List<CalculableSentence>,
    latestExpiryDate: LocalDate,
    latestReleaseDate: LocalDate,
    returnToCustodyDate: LocalDate?,
    latestSled: LocalDate?,
  ): LocalDate {
    if (!featureToggles.revisedFixedTermRecallsRules || returnToCustodyDate == null || latestSled == null) {
      return latestReleaseDate
    }

    val fixedTermRecallSentences = getFixedTermRecallSentences(sentences)

    if (!ftrIsMixedDuration(fixedTermRecallSentences)) {
      return latestReleaseDate
    }

    val latestSentence = sentences.find {
      it.sentenceCalculation.adjustedExpiryDate == latestSled
    } ?: return latestReleaseDate

    val maxPostRecallDate = returnToCustodyDate
      .plusDays(if (latestSentence.recallType == FIXED_TERM_RECALL_14) 13 else 27)
      .plusDays(latestSentence.sentenceCalculation.adjustments.adjustmentsForFixedTermRecall())

    return if (latestSentence.durationIsGreaterThanOrEqualTo(12, MONTHS)) {
      getReleaseDate(latestSentence, maxPostRecallDate)
    } else {
      calculateUnderTwelveMonthsReleaseDate(
        fixedTermRecallSentences,
        latestSentence,
        returnToCustodyDate,
        maxPostRecallDate,
        latestReleaseDate,
      )
    }
  }

  fun hasHomeDetentionCurfew(dates: Map<ReleaseDateType, LocalDate>): Boolean {
    val postRecallReleaseDate = dates[PRRD] ?: return false
    val latestDateEntry =
      dates.filterKeys { it == CRD || it == ARD || it == NPD }.maxByOrNull { it.value } ?: return false
    return postRecallReleaseDate != dates[CRD] && postRecallReleaseDate.isBefore(latestDateEntry.value)
  }

  private fun ftrIsMixedDuration(sentences: List<CalculableSentence>): Boolean {
    return sentences.any {
      it.durationIsGreaterThanOrEqualTo(12, MONTHS)
    } && sentences.any {
      it.durationIsLessThan(12, MONTHS)
    }
  }

  private fun getReleaseDate(sentence: CalculableSentence, maxReleaseDate: LocalDate): LocalDate {
    return if (maxReleaseDate < sentence.sentenceCalculation.adjustedExpiryDate) {
      maxReleaseDate
    } else {
      sentence.sentenceCalculation.adjustedExpiryDate
    }
  }

  private fun calculateUnderTwelveMonthsReleaseDate(
    sentences: List<CalculableSentence>,
    latestSentence: CalculableSentence,
    returnToCustodyDate: LocalDate,
    maxReleaseDate: LocalDate,
    latestReleaseDate: LocalDate,
  ): LocalDate {
    val adjacentOverTwelveMonthSentence =
      getAdjacentOverTwelveMonthSentence(sentences, latestReleaseDate, returnToCustodyDate) ?: return latestReleaseDate

    return when {
      is28DayWithGapLessThan14Days(
        latestSentence,
        adjacentOverTwelveMonthSentence,
        returnToCustodyDate,
      ) -> getReleaseDate(adjacentOverTwelveMonthSentence, maxReleaseDate)

      is14DayWithGapLessThan14Days(
        latestSentence,
        adjacentOverTwelveMonthSentence,
        returnToCustodyDate,
      ) -> getReleaseDate(latestSentence, maxReleaseDate)

      else -> latestReleaseDate
    }
  }

  private fun getAdjacentOverTwelveMonthSentence(
    sentences: List<CalculableSentence>,
    latestReleaseDate: LocalDate,
    returnToCustodyDate: LocalDate,
  ): CalculableSentence? {
    return sentences
      .filter {
        it.durationIsGreaterThanOrEqualTo(12, MONTHS) && it.sentenceCalculation.adjustedExpiryDate.isAfterOrEqualTo(
          returnToCustodyDate,
        )
      }
      .minByOrNull { abs(ChronoUnit.DAYS.between(it.sentenceCalculation.unadjustedExpiryDate, latestReleaseDate)) }
  }

  private fun is28DayWithGapLessThan14Days(
    latestSentence: CalculableSentence,
    adjacentSentence: CalculableSentence,
    returnToCustodyDate: LocalDate,
  ): Boolean {
    return latestSentence.recallType == FIXED_TERM_RECALL_28 &&
      ChronoUnit.DAYS.between(adjacentSentence.sentenceCalculation.adjustedExpiryDate, returnToCustodyDate) < 14
  }

  private fun is14DayWithGapLessThan14Days(
    latestSentence: CalculableSentence,
    adjacentSentence: CalculableSentence,
    returnToCustodyDate: LocalDate,
  ): Boolean {
    return latestSentence.recallType == FIXED_TERM_RECALL_14 &&
      ChronoUnit.DAYS.between(adjacentSentence.sentenceCalculation.adjustedExpiryDate, returnToCustodyDate) < 14
  }

  private fun getFixedTermRecallSentences(sentences: List<CalculableSentence>): List<CalculableSentence> =
    sentences
      .filter {
        it.recallType == FIXED_TERM_RECALL_28 ||
          it.recallType == FIXED_TERM_RECALL_14 &&
          it.releaseDateTypes.contains(SLED)
      }
      .sortedBy { it.sentencedAt }
}
