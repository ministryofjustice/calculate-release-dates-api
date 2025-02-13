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
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.ChronoUnit.MONTHS

@Service
class FixedTermRecallsService(private val featureToggles: FeatureToggles) {

  fun calculatePRRD(
    sentences: List<CalculableSentence>,
    latestExpiryDate: LocalDate,
    returnToCustodyDate: LocalDate?,
    latestReleaseDate: LocalDate,
  ): LocalDate {
    if (!featureToggles.revisedFixedTermRecallsRules || returnToCustodyDate == null) {
      return latestReleaseDate
    }

    val fixedTermRecallSentences = getFixedTermRecallSentences(sentences)

    if (!ftrIsMixedDuration(fixedTermRecallSentences)) {
      return latestReleaseDate
    }

    val latestSentence = getLatestSentence(fixedTermRecallSentences) ?: return latestReleaseDate

    val maxPostRecallDate = returnToCustodyDate
      .plusDays(if (latestSentence.recallType == FIXED_TERM_RECALL_14) 13 else 27)
      .plusDays(latestSentence.sentenceCalculation.adjustments.ualAfterFtr)
      .plusDays(latestSentence.sentenceCalculation.adjustments.awardedDuringCustody)

    return if (latestSentence.durationIsGreaterThanOrEqualTo(12, MONTHS)) {
      calculateOverTwelveMonthsReleaseDate(latestSentence, maxPostRecallDate)
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
    val latestDateEntry = dates.filterKeys { it == CRD || it == ARD || it == NPD }.maxByOrNull { it.value } ?: return false
    return postRecallReleaseDate != dates[CRD] && postRecallReleaseDate.isBefore(latestDateEntry.value)
  }

  private fun ftrIsMixedDuration(sentences: List<CalculableSentence>): Boolean {
    return sentences.any {
      it.durationIsGreaterThanOrEqualTo(12, MONTHS)
    } && sentences.any {
      it.durationIsLessThan(12, MONTHS)
    }
  }

  private fun getLatestSentence(sentences: List<CalculableSentence>): CalculableSentence? {
    return sentences.maxByOrNull { it.sentenceCalculation.adjustedExpiryDate }
  }

  private fun calculateOverTwelveMonthsReleaseDate(sentence: CalculableSentence, maxReleaseDate: LocalDate): LocalDate {
    return if (maxReleaseDate < sentence.sentenceCalculation.adjustedExpiryDate) {
      maxReleaseDate
    } else {
      sentence.sentenceCalculation.releaseDate
    }
  }

  private fun calculateUnderTwelveMonthsReleaseDate(
    sentences: List<CalculableSentence>,
    latestSentence: CalculableSentence,
    returnToCustodyDate: LocalDate,
    maxReleaseDate: LocalDate,
    latestReleaseDate: LocalDate,
  ): LocalDate {
    val adjacentOverTwelveMonthSentence = getAdjacentOverTwelveMonthSentence(sentences) ?: return latestReleaseDate
    return when {
      is28DayWithGapLessThan14Days(latestSentence, adjacentOverTwelveMonthSentence, returnToCustodyDate) -> getReleaseDate(adjacentOverTwelveMonthSentence, maxReleaseDate)
      is14DayWithGapLessThan14Days(latestSentence, adjacentOverTwelveMonthSentence, returnToCustodyDate) -> getReleaseDate(latestSentence, maxReleaseDate)
      else -> latestReleaseDate
    }
  }

  private fun getAdjacentOverTwelveMonthSentence(sentences: List<CalculableSentence>): CalculableSentence? {
    return sentences
      .filter { it.durationIsGreaterThanOrEqualTo(12, MONTHS) }
      .maxByOrNull { it.sentenceCalculation.adjustedExpiryDate }
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

  private fun getReleaseDate(sentence: CalculableSentence, maxReleaseDate: LocalDate): LocalDate {
    return if (maxReleaseDate < sentence.sentenceCalculation.adjustedExpiryDate) {
      maxReleaseDate
    } else {
      sentence.sentenceCalculation.releaseDate
    }
  }

  private fun getFixedTermRecallSentences(sentences: List<CalculableSentence>): List<CalculableSentence> {
    return sentences
      .filter { it.recallType == FIXED_TERM_RECALL_28 || it.recallType == FIXED_TERM_RECALL_14 && it.releaseDateTypes.contains(SLED) }
      .sortedBy { it.sentencedAt }
  }
}
