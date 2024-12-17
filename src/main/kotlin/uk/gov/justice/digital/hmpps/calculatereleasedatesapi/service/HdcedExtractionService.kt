package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationRule
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.HDCED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.HDCED365
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

    if (sentences.none { it.isSDSPlus }) {
      val latestEligibleSentence = getLatestHdcedEligibleSentence(sentences)

      if (hasLatestEligibleSentenceGotHdcedDate(latestEligibleSentence)) {
        val hdcedSentence = getMostRecentHDCEDSentenceApplying14DayRule(sentences, latestAdjustedReleaseDate)

        val conflictingSentence = getLatestConflictingNonHdcSentence(
          sentences,
          hdcedSentence?.sentenceCalculation?.homeDetentionCurfewEligibilityDate,
          hdcedSentence,
        )

        if (hdcedSentence != null) {
          if (latestAdjustedReleaseDateIsAfterHdced(hdcedSentence.sentenceCalculation, latestAdjustedReleaseDate)) {
            return resolveEligibilityDate(hdcedSentence, conflictingSentence)
          }
        }
      }
    }
    return null
  }

  // This is a copy of extractManyHomeDetentionCurfewEligibilityDate method - the only difference being it saves against the new HDC-365 variant and uses it's config
  // Duplicated the method rather than modified it with switches as the other method can just be deleted after the HDC365 commencement date
  fun extractManyHomeDetentionCurfewEligibilityDateHDC365(
    sentences: List<CalculableSentence>,
    mostRecentSentencesByReleaseDate: List<CalculableSentence>,
  ): Pair<LocalDate, ReleaseDateCalculationBreakdown>? {
    val latestAdjustedReleaseDate = getLatestAdjustedReleaseDate(mostRecentSentencesByReleaseDate)

    if (sentences.none { it.isSDSPlus }) {
      val latestEligibleSentence = getLatestHdcedEligibleSentence(sentences)

      if (hasLatestEligibleSentenceGotHdced365Date(latestEligibleSentence)) {
        val hdcedSentence = getMostRecentHDCED365SentenceApplying14DayRule(sentences, latestAdjustedReleaseDate)

        val conflictingSentence = getLatestConflictingNonHdc365Sentence(
          sentences,
          hdcedSentence?.sentenceCalculation?.homeDetentionCurfewEligibilityDateHDC365,
          hdcedSentence,
        )

        if (hdcedSentence != null) {
          if (latestAdjustedReleaseDateIsAfterHdced365(hdcedSentence.sentenceCalculation, latestAdjustedReleaseDate)) {
            return resolveEligibilityDateHDC365(hdcedSentence, conflictingSentence)
          }
        }
      }
    }
    return null
  }

  fun latestAdjustedReleaseDateIsAfterHdced(
    sentenceCalculation: SentenceCalculation,
    latestReleaseDate: LocalDate,
  ): Boolean {
    val hdcedDate = sentenceCalculation.homeDetentionCurfewEligibilityDate
    return hdcedDate?.let { latestReleaseDate.isAfter(it) } ?: false
  }

  // This is a copy of latestAdjustedReleaseDateIsAfterHdced for HDC-365
  fun latestAdjustedReleaseDateIsAfterHdced365(
    sentenceCalculation: SentenceCalculation,
    latestReleaseDate: LocalDate,
  ): Boolean {
    val hdcedDate = sentenceCalculation.homeDetentionCurfewEligibilityDateHDC365
    return hdcedDate?.let { latestReleaseDate.isAfter(it) } ?: false
  }

  fun releaseDateIsAfterHdced(sentenceCalculation: SentenceCalculation): Boolean {
    val releaseDate = sentenceCalculation.releaseDate
    val hdcedDate = sentenceCalculation.homeDetentionCurfewEligibilityDate
    return hdcedDate?.let { releaseDate.isAfter(it) } ?: false
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

  // Copy of hasLatestEligibleSentenceGotHdcedDate for HDC-365
  private fun hasLatestEligibleSentenceGotHdced365Date(latestEligibleSentences: CalculableSentence?): Boolean =
    latestEligibleSentences?.sentenceCalculation?.homeDetentionCurfewEligibilityDateHDC365 != null

  private fun getMostRecentHDCEDSentenceApplying14DayRule(
    sentences: List<CalculableSentence>,
    latestReleaseDate: LocalDate,
  ): CalculableSentence? {
    return extractionService.mostRecentSentenceOrNull(
      sentences.filter { !latestReleaseDate.isBefore(it.sentencedAt.plusDays(14)) },
      SentenceCalculation::homeDetentionCurfewEligibilityDate,
    )
  }

  // Copy of getMostRecentHDCEDSentenceApplying14DayRule for HDC-365
  private fun getMostRecentHDCED365SentenceApplying14DayRule(
    sentences: List<CalculableSentence>,
    latestReleaseDate: LocalDate,
  ): CalculableSentence? {
    return extractionService.mostRecentSentenceOrNull(
      sentences.filter { !latestReleaseDate.isBefore(it.sentencedAt.plusDays(14)) },
      SentenceCalculation::homeDetentionCurfewEligibilityDateHDC365,
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

  // Copy of getLatestConflictingNonHdcSentence for HDC-365
  private fun getLatestConflictingNonHdc365Sentence(
    sentences: List<CalculableSentence>,
    hdcedDate: LocalDate?,
    hdcedSentence: CalculableSentence?,
  ): Pair<CalculableSentence?, LocalDate?> {
    return getLatestConflictingSentence(
      sentences.filter { it.sentenceCalculation.homeDetentionCurfewEligibilityDateHDC365 == null },
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

  // Copy of resolveEligibilityDate for HDC-365
  private fun resolveEligibilityDateHDC365(
    hdcedSentence: CalculableSentence,
    conflictingSentence: Pair<CalculableSentence?, LocalDate?>,
  ): Pair<LocalDate, ReleaseDateCalculationBreakdown> {
    return if (hdcedSentence != conflictingSentence.first &&
      conflictingSentence.first != null &&
      conflictingSentence.second != null
    ) {
      getReleaseDateCalculationBreakDownFromLatestConflictingSentence(
        conflictingSentence,
        hdcedSentence.sentenceCalculation.homeDetentionCurfewEligibilityDateHDC365!!,
      )
    } else {
      hdcedSentence.sentenceCalculation.homeDetentionCurfewEligibilityDateHDC365!! to
        hdcedSentence.sentenceCalculation.breakdownByReleaseDateType[HDCED365]!!
    }
  }
}
