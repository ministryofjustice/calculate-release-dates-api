package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.stereotype.Service
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
import kotlin.math.abs

@Service
class FixedTermRecallsService {

  fun calculatePRRD(
    sentences: List<CalculableSentence>,
    latestExpiryDate: LocalDate,
    returnToCustodyDate: LocalDate?,
    latestReleaseDate: LocalDate,
  ): LocalDate {
    if (returnToCustodyDate == null) {
      return latestReleaseDate
    }

    val fixedTermRecallSentences = getFixedTermRecallSentences(sentences)

    val sledSentence = getSledSentence(fixedTermRecallSentences, latestExpiryDate)
    val previousSentence = getPreviousSentence(fixedTermRecallSentences, sledSentence)

    return if (sledSentence != null && previousSentence != null) {
      findMixedDurationReleaseDate(sledSentence, previousSentence, returnToCustodyDate)
    } else {
      latestReleaseDate
    }
  }

  fun hasHomeDetentionCurfew(dates: Map<ReleaseDateType, LocalDate>): Boolean {
    val postRecallReleaseDate = dates[PRRD] ?: return false
    val latestDateEntry = dates.filterKeys { it == CRD || it == ARD || it == NPD }.maxByOrNull { it.value } ?: return false
    return postRecallReleaseDate != dates[CRD] && postRecallReleaseDate.isBefore(latestDateEntry.value)
  }

  private fun getFixedTermRecallSentences(sentences: List<CalculableSentence>): List<CalculableSentence> {
    return sentences.filter {
      it.recallType == FIXED_TERM_RECALL_28 || it.recallType == FIXED_TERM_RECALL_14 &&
        it.releaseDateTypes.contains(SLED)
    }.sortedBy { it.sentencedAt }
  }

  private fun getSledSentence(
    fixedTermRecallSentences: List<CalculableSentence>,
    latestExpiryDate: LocalDate,
  ): CalculableSentence? {
    return fixedTermRecallSentences.lastOrNull { it.sentenceCalculation.expiryDate == latestExpiryDate }
  }

  private fun getPreviousSentence(
    fixedTermRecallSentences: List<CalculableSentence>,
    sledSentence: CalculableSentence?,
  ): CalculableSentence? {
    return fixedTermRecallSentences.filterNot { it.sentencedAt == sledSentence?.sentencedAt }.lastOrNull()
  }

  private fun getRecallSentenceDuration(sentence: CalculableSentence): Long =
    if (sentence.recallType == FIXED_TERM_RECALL_14) 14 else 28

  private fun findMixedDurationReleaseDate(
    sledSentence: CalculableSentence,
    previousSentence: CalculableSentence,
    returnToCustodyDate: LocalDate,
  ): LocalDate {
    if (sledSentence.durationIsLessThan(12, MONTHS) && previousSentence.durationIsLessThan(12, MONTHS)) {
      return sledSentence.sentenceCalculation.releaseDate
    }

    val (twelveMonthReleaseDate, elevenMonthReleaseDate) =
      if (sledSentence.durationIsLessThan(12, MONTHS)) {
        previousSentence.sentenceCalculation.releaseDate to sledSentence.sentenceCalculation.releaseDate
      } else {
        sledSentence.sentenceCalculation.releaseDate to previousSentence.sentenceCalculation.releaseDate
      }

    val daysToTwelveMonthRelease =
      ChronoUnit.DAYS.between(returnToCustodyDate, twelveMonthReleaseDate)

    val recallDuration = getRecallSentenceDuration(sledSentence)
    val adjustedRecallDate = returnToCustodyDate.plusDays(recallDuration)

    if (abs(daysToTwelveMonthRelease) < 14) {
      return if (adjustedRecallDate < elevenMonthReleaseDate) adjustedRecallDate else elevenMonthReleaseDate
    }

    return if (adjustedRecallDate <= twelveMonthReleaseDate) {
      adjustedRecallDate
    } else {
      twelveMonthReleaseDate
    }
  }
}
