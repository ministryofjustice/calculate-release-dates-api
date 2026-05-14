package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.FeatureToggles
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationRule
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.HDCED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculableSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ReleaseDateCalculationBreakdown
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.StandardDeterminateSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Term
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.ImportantDates.PROGRESSION_COMMENCEMENT_DATE
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.sentence.SentencesExtractionService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.isAfterOrEqualTo
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Service
class HdcedExtractionService(
  val extractionService: SentencesExtractionService,
  val featureToggles: FeatureToggles,
) {

  /**
   * Extracts the Home Detention Curfew Eligibility Date (HDCED) from a list of sentences.
   *
   * The HDCED will be considered for the booking only if all the following are true:
   * - The booking contains no SDS+ sentences.
   * - The latest eligible sentence has a calculated HDCED.
   * - A valid HDCED sentence exists after applying a 14-day rule.
   * - The final release date for the prisoner is after their calculated HDCED.
   *
   * Additionally, under the "Post HDCED Repeal" rules (controlled by a feature toggle), the HDCED may be nullified if:
   * - The list of sentences include more than SEC250 or Term based sentences. SEC250 nnd Terms retain HDCED eligibility.
   *   And the calculation is being performed on or after the progression commencement date, OR the calculated HDCED falls on or after that date.
   */
  fun extractManyHomeDetentionCurfewEligibilityDate(
    sentences: List<CalculableSentence>,
    mostRecentSentencesByReleaseDate: List<CalculableSentence>,
  ): Pair<LocalDate, ReleaseDateCalculationBreakdown>? {
    if (sentences.any { it is StandardDeterminateSentence && it.releaseArrangements.isSDSPlusEligibleSentenceTypeLengthAndOffence }) return null
    if (!hasLatestEligibleSentenceGotHdcedDate(getLatestHdcedEligibleSentence(sentences))) return null

    val latestAdjustedReleaseDate = getLatestAdjustedReleaseDate(mostRecentSentencesByReleaseDate)
    val hdcedSentence = getMostRecentHDCEDSentenceApplying14DayRule(sentences, latestAdjustedReleaseDate) ?: return null
    val hdcDate = hdcedSentence.sentenceCalculation.homeDetentionCurfewEligibilityDate ?: return null
    val conflictingSentence = getLatestConflictingNonHdcSentence(sentences, hdcDate, hdcedSentence)

    if (latestAdjustedReleaseDateIsAfterHdced(hdcedSentence.sentenceCalculation, latestAdjustedReleaseDate)) {
      val hdcedResult = resolveEligibilityDate(hdcedSentence, conflictingSentence)

      if (featureToggles.applyPostHdcedRepealRules) {
        val sentencesAffectedByHdcedRepeal = sentences.any { !(it is StandardDeterminateSentence && it.releaseArrangements.isSection250 || it is Term) }
        val today = LocalDate.now()
        if (sentencesAffectedByHdcedRepeal && (today.isAfterOrEqualTo(PROGRESSION_COMMENCEMENT_DATE) || hdcedResult.first.isAfterOrEqualTo(PROGRESSION_COMMENCEMENT_DATE))) {
          return null
        }
      }

      return hdcedResult
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

  fun releaseDateIsAfterHdced(sentenceCalculation: SentenceCalculation): Boolean {
    val releaseDate = sentenceCalculation.releaseDate
    val hdcedDate = sentenceCalculation.homeDetentionCurfewEligibilityDate
    return hdcedDate?.let { releaseDate.isAfter(it) } ?: false
  }

  private fun getLatestConflictingSentence(
    sentenceGroup: List<CalculableSentence>,
    calculatedHDCED: LocalDate,
    hdcSentence: CalculableSentence,
  ): Pair<CalculableSentence?, LocalDate?> {
    val otherNonSentencesInGroup =
      sentenceGroup.filter {
        it != hdcSentence &&
          it.sentenceCalculation.releaseDateDefaultedByCommencement().isAfter(calculatedHDCED) &&
          !it.isDto()
      }
    val containsOnlyImmediateRelease = otherNonSentencesInGroup.all { it.sentenceCalculation.isImmediateRelease() && !it.isRecall() }

    val nextApplicableSentence =
      extractionService.mostRecentSentenceOrNull(
        otherNonSentencesInGroup,
        SentenceCalculation::releaseDate,
      )

    if (nextApplicableSentence != null) {
      if (!containsOnlyImmediateRelease || nextApplicableSentence.sentenceCalculation.isImmediateCustodyRelease()) {
        return nextApplicableSentence to nextApplicableSentence.sentenceCalculation.releaseDateDefaultedByCommencement()
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

  private fun getLatestHdcedEligibleSentence(sentences: List<CalculableSentence>): CalculableSentence? = extractionService.mostRecentSentenceOrNull(
    sentences.filter { !it.isRecall() && !it.isDto() },
    SentenceCalculation::releaseDate,
  )

  private fun getLatestAdjustedReleaseDate(mostRecentSentencesByReleaseDate: List<CalculableSentence>): LocalDate = mostRecentSentencesByReleaseDate[0].sentenceCalculation.releaseDate

  private fun hasLatestEligibleSentenceGotHdcedDate(latestEligibleSentences: CalculableSentence?): Boolean = latestEligibleSentences?.sentenceCalculation?.homeDetentionCurfewEligibilityDate != null

  private fun getMostRecentHDCEDSentenceApplying14DayRule(
    sentences: List<CalculableSentence>,
    latestReleaseDate: LocalDate,
  ): CalculableSentence? = extractionService.mostRecentSentenceOrNull(
    sentences.filter { !latestReleaseDate.isBefore(it.sentencedAt.plusDays(14)) },
    SentenceCalculation::homeDetentionCurfewEligibilityDate,
  )

  private fun getLatestConflictingNonHdcSentence(
    sentences: List<CalculableSentence>,
    hdcedDate: LocalDate,
    hdcedSentence: CalculableSentence,
  ): Pair<CalculableSentence?, LocalDate?> = getLatestConflictingSentence(
    sentences.filter { it.sentenceCalculation.homeDetentionCurfewEligibilityDate == null },
    hdcedDate,
    hdcedSentence,
  )

  private fun resolveEligibilityDate(
    hdcedSentence: CalculableSentence,
    conflictingSentence: Pair<CalculableSentence?, LocalDate?>,
  ): Pair<LocalDate, ReleaseDateCalculationBreakdown> = if (hdcedSentence != conflictingSentence.first &&
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
