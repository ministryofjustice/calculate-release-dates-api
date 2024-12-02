package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationRule
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.HDCED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculableSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ReleaseDateCalculationBreakdown
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceCalculation
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Service
class HdcedExtractionService(
  val extractionService: SentencesExtractionService,
) {

  fun extractManyHomeDetentionCurfewEligibilityDate(
    sentences: List<CalculableSentence>,
    mostRecentSentencesByReleaseDate: List<CalculableSentence>,
  ): Pair<LocalDate, ReleaseDateCalculationBreakdown>? {
    val latestAdjustedReleaseDate = getLatestAdjustedReleaseDate(mostRecentSentencesByReleaseDate)

    if (sentences.none { it.isSDSPlusEligibleSentenceAndOffence }) {
      val latestEligibleSentence = getLatestHdcedEligibleSentence(sentences)

      if (hasLatestEligibleSentenceGotHdcedDate(latestEligibleSentence)) {
        val hdcedSentence = getMostRecentHDCEDSentenceApplying14DayRule(sentences, latestAdjustedReleaseDate)

        val conflictingSentence = getLatestConflictingNonHdcSentence(
          sentences,
          hdcedSentence?.sentenceCalculation?.homeDetentionCurfewEligibilityDate,
          hdcedSentence,
        )

        if (hdcedSentence != null) {
          return resolveEligibilityDate(hdcedSentence, conflictingSentence)
        }
      }
    }
    return null
  }

  private fun getLatestConflictingSentence(
    sentenceGroup: List<CalculableSentence>,
    calculatedHDCED: LocalDate?,
    hdcSentence: CalculableSentence?,
  ): Pair<CalculableSentence?, LocalDate?> {
    val otherNonSentencesInGroup =
      sentenceGroup.filter {
        it != hdcSentence &&
          it.sentenceCalculation.releaseDate.isAfter(calculatedHDCED) &&
          !it.isDto()
      }
    val containsOnlyImmediateRelease = otherNonSentencesInGroup.all { it.sentenceCalculation.isImmediateRelease() }

    val nextApplicableSentence =
      extractionService.mostRecentSentenceOrNull(
        otherNonSentencesInGroup,
        SentenceCalculation::releaseDate,
      )

    if (nextApplicableSentence != null) {
      if (!containsOnlyImmediateRelease || nextApplicableSentence.sentenceCalculation.isImmediateCustodyRelease()) {
        return nextApplicableSentence to nextApplicableSentence.sentenceCalculation.releaseDate
      }
    }

    return hdcSentence to calculatedHDCED
  }
  private fun getReleaseDateCalculationBreakDownFromLatestConflictingSentence(
    latestConflictingSentence: Pair<CalculableSentence?, LocalDate?>,
    hdcedSentenceDate: LocalDate,
  ): Pair<LocalDate, ReleaseDateCalculationBreakdown> {
    val adjustedReleaseDate = latestConflictingSentence.second!!
    val releaseDateTypes = latestConflictingSentence.first!!.releaseDateTypes
    val calculationRule = when {
      ReleaseDateType.ARD in releaseDateTypes -> CalculationRule.HDCED_ADJUSTED_TO_CONCURRENT_ACTUAL_RELEASE
      ReleaseDateType.PRRD in releaseDateTypes -> CalculationRule.HDCED_ADJUSTED_TO_CONCURRENT_PRRD
      else -> CalculationRule.HDCED_ADJUSTED_TO_CONCURRENT_CONDITIONAL_RELEASE
    }

    return adjustedReleaseDate to ReleaseDateCalculationBreakdown(
      rules = setOf(calculationRule),
      releaseDate = adjustedReleaseDate,
      unadjustedDate = hdcedSentenceDate,
      adjustedDays = ChronoUnit.DAYS.between(adjustedReleaseDate, hdcedSentenceDate),
    )
  }

  private fun getLatestHdcedEligibleSentence(sentences: List<CalculableSentence>): CalculableSentence? {
    return extractionService.mostRecentSentenceOrNull(
      sentences.filter { !it.isRecall() && !it.isDto() },
      SentenceCalculation::releaseDate,
    )
  }

  private fun getLatestAdjustedReleaseDate(mostRecentSentencesByReleaseDate: List<CalculableSentence>): LocalDate {
    return mostRecentSentencesByReleaseDate[0].sentenceCalculation.releaseDate
  }

  private fun hasLatestEligibleSentenceGotHdcedDate(latestEligibleSentences: CalculableSentence?): Boolean {
    return latestEligibleSentences?.sentenceCalculation?.homeDetentionCurfewEligibilityDate != null
  }

  private fun getMostRecentHDCEDSentenceApplying14DayRule(
    sentences: List<CalculableSentence>,
    latestReleaseDate: LocalDate,
  ): CalculableSentence? {
    return extractionService.mostRecentSentenceOrNull(
      sentences.filter { !latestReleaseDate.isBefore(it.sentencedAt.plusDays(14)) },
      SentenceCalculation::homeDetentionCurfewEligibilityDate,
    )
  }

  private fun getLatestConflictingNonHdcSentence(
    sentences: List<CalculableSentence>,
    hdcedDate: LocalDate?,
    hdcedSentence: CalculableSentence?,
  ): Pair<CalculableSentence?, LocalDate?> {
    return getLatestConflictingSentence(
      sentences.filter { it.sentenceCalculation.homeDetentionCurfewEligibilityDate == null },
      hdcedDate,
      hdcedSentence,
    )
  }

  private fun resolveEligibilityDate(
    hdcedSentence: CalculableSentence,
    conflictingSentence: Pair<CalculableSentence?, LocalDate?>,
  ): Pair<LocalDate, ReleaseDateCalculationBreakdown> {
    return if (hdcedSentence != conflictingSentence.first &&
      conflictingSentence.first != null &&
      conflictingSentence.second != null
    ) {
      getReleaseDateCalculationBreakDownFromLatestConflictingSentence(
        conflictingSentence,
        hdcedSentence.sentenceCalculation.homeDetentionCurfewEligibilityDate!!,
      )
    } else {
      hdcedSentence.sentenceCalculation.homeDetentionCurfewEligibilityDate!! to
        hdcedSentence.sentenceCalculation.breakdownByReleaseDateType[HDCED]!!
    }
  }
}
