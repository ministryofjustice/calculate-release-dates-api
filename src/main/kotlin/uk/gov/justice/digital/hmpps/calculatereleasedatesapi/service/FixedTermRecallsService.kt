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
import java.time.temporal.ChronoUnit.MONTHS

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
    val latestDateEntry = dates.filterKeys { it == CRD || it == ARD || it == NPD }.maxByOrNull { it.value }
    return postRecallReleaseDate != dates[CRD] && latestDateEntry != null && postRecallReleaseDate.isBefore(latestDateEntry.value)
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

  private fun getLatestReleaseDate(currentSentence: CalculableSentence, previousSentence: CalculableSentence) =
    if (currentSentence.sentenceCalculation.expiryDate > previousSentence.sentenceCalculation.expiryDate) {
      previousSentence.sentenceCalculation.releaseDate
    } else {
      currentSentence.sentenceCalculation.releaseDate
    }

  private fun getRecallSentenceDuration(sentence: CalculableSentence): Long =
    if (sentence.recallType == FIXED_TERM_RECALL_14) 14 else 28

  private fun getLatestSentenceForDuration(currentSentence: CalculableSentence, previousSentence: CalculableSentence) =
    if (
      (currentSentence.recallType == FIXED_TERM_RECALL_14 && currentSentence.durationIsLessThan(12, MONTHS)) ||
      (currentSentence.recallType == FIXED_TERM_RECALL_28 && currentSentence.durationIsGreaterThan(12, MONTHS))
    ) {
      currentSentence
    } else {
      previousSentence
    }

  private fun findMixedDurationReleaseDate(
    sledSentence: CalculableSentence,
    previousSentence: CalculableSentence,
    returnToCustodyDate: LocalDate,
  ): LocalDate {
    if (sledSentence.durationIsLessThan(12, MONTHS) && previousSentence.durationIsLessThan(12, MONTHS)) {
      return sledSentence.sentenceCalculation.releaseDate
    }

    val recallDuration = getRecallSentenceDuration(sledSentence)
    val recallDatePlusRecallDuration = returnToCustodyDate.plusDays(recallDuration)

    val latestSentenceForDuration = getLatestSentenceForDuration(sledSentence, previousSentence)

    return if (recallDatePlusRecallDuration > latestSentenceForDuration.sentenceCalculation.releaseDate) {
      latestSentenceForDuration.sentenceCalculation.releaseDate
    } else {
      getLatestReleaseDate(sledSentence, previousSentence)
    }
  }
}
