package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationRule
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationRule.ERSED_ADJUSTED_TO_MTD
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.HistoricalTusedSource
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.ARD
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.CRD
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.ERSED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.ESED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.ETD
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.HDCED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.LED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.LTD
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.MTD
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.NCRD
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.NPD
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.PED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.PRRD
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.SED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.SLED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.TUSED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceIdentificationTrack
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.NoSentencesProvidedException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AFineSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.BotusSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculableSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offender
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ReleaseDateCalculationBreakdown
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.sentence.oraAndNoneOraExtraction
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.isAfterOrEqualTo
import java.time.LocalDate
import java.time.Period
import java.time.temporal.ChronoUnit
import java.time.temporal.ChronoUnit.MONTHS

@Service
class BookingExtractionService(
  val hdcedExtractionService: HdcedExtractionService,
  val extractionService: SentencesExtractionService,
  val fixedTermRecallsService: FixedTermRecallsService,
) {

  fun extract(
    sentences: List<CalculableSentence>,
    sentenceGroups: List<List<CalculableSentence>>,
    offender: Offender,
    returnToCustodyDate: LocalDate? = null,
  ): CalculationResult = when (sentences.size) {
    0 -> throw NoSentencesProvidedException("At least one sentence must be provided")
    1 -> extractSingle(sentences[0])
    else -> {
      extractMultiple(sentences, sentenceGroups, offender, returnToCustodyDate)
    }
  }

  private fun extractSingle(sentence: CalculableSentence): CalculationResult {
    val dates: MutableMap<ReleaseDateType, LocalDate> = mutableMapOf()
    val sentenceCalculation = sentence.sentenceCalculation
    var historicalTusedSource: HistoricalTusedSource? = null
    val sentenceIsOrExclusivelyBotus = sentence.isOrExclusivelyBotus()

    if (sentence.releaseDateTypes.contains(SLED)) {
      dates[SLED] = sentenceCalculation.expiryDate
    } else {
      dates[SED] = sentenceCalculation.expiryDate
    }

    dates[sentence.getReleaseDateType()] = sentenceCalculation.releaseDate

    if (sentenceCalculation.licenceExpiryDate != null &&
      sentence.releaseDateTypes.getReleaseDateTypes().contains(LED)
    ) {
      dates[LED] = sentenceCalculation.licenceExpiryDate!!
    }

    if (sentenceCalculation.nonParoleDate != null) {
      dates[NPD] = sentenceCalculation.nonParoleDate!!
    }

    if (sentenceCalculation.topUpSupervisionDate != null) {
      dates[TUSED] = sentenceCalculation.topUpSupervisionDate!!
    }

    if (sentenceCalculation.homeDetentionCurfewEligibilityDate != null && !sentence.releaseDateTypes.contains(PED)) {
      if (hdcedExtractionService.releaseDateIsAfterHdced(sentenceCalculation)) {
        dates[HDCED] = sentenceCalculation.homeDetentionCurfewEligibilityDate!!
      }
    }

    if (sentenceCalculation.notionalConditionalReleaseDate != null) {
      dates[NCRD] = sentenceCalculation.notionalConditionalReleaseDate!!
    }

    if (sentenceCalculation.extendedDeterminateParoleEligibilityDate != null) {
      dates[PED] = sentenceCalculation.extendedDeterminateParoleEligibilityDate!!
    }

    if (sentenceCalculation.earlyReleaseSchemeEligibilityDate != null) {
      dates[ERSED] = sentenceCalculation.earlyReleaseSchemeEligibilityDate!!
    }

    if (sentenceCalculation.earlyTransferDate != null) {
      dates[ETD] = sentenceCalculation.earlyTransferDate!!
    }

    if (sentenceCalculation.latestTransferDate != null) {
      dates[LTD] = sentenceCalculation.latestTransferDate!!
    }

    if (!sentenceIsOrExclusivelyBotus) {
      dates[ESED] = sentenceCalculation.unadjustedExpiryDate
    }

    if (
      sentenceIsOrExclusivelyBotus &&
      sentence is BotusSentence &&
      sentence.latestTusedDate != null &&
      sentenceCalculation.expiryDate.isBefore(sentence.latestTusedDate)
    ) {
      dates[TUSED] = sentence.latestTusedDate!!
      historicalTusedSource = sentence.latestTusedSource!!
    }

    return CalculationResult(
      dates.toMap(),
      sentenceCalculation.breakdownByReleaseDateType.toMap(),
      emptyMap(),
      getEffectiveSentenceLength(sentence.sentencedAt, sentenceCalculation.unadjustedExpiryDate),
      false,
      historicalTusedSource,
      affectedBySds40 = isAffectedBySds40(sentence),
    )
  }

  /**
   *  Method applies business logic for when there are multiple sentences in a booking and the impact each may have on one another
   */
  private fun extractMultiple(
    sentences: List<CalculableSentence>,
    sentenceGroups: List<List<CalculableSentence>>,
    offender: Offender,
    returnToCustodyDate: LocalDate?,
  ): CalculationResult {
    val dates: MutableMap<ReleaseDateType, LocalDate> = mutableMapOf()
    val otherDates: MutableMap<ReleaseDateType, LocalDate> = mutableMapOf()
    val breakdownByReleaseDateType: MutableMap<ReleaseDateType, ReleaseDateCalculationBreakdown> = mutableMapOf()
    val earliestSentenceDate = sentences.minOf { it.sentencedAt }

    val mostRecentSentencesByReleaseDate =
      extractionService.mostRecentSentences(sentences, SentenceCalculation::releaseDateDefaultedByCommencement)
    val mostRecentSentenceByAdjustedDeterminateReleaseDate =
      extractionService.mostRecentSentence(sentences, SentenceCalculation::adjustedDeterminateReleaseDate)
    val mostRecentSentenceByExpiryDate =
      extractionService.mostRecentSentence(sentences, SentenceCalculation::expiryDate)

    val latestReleaseDate = mostRecentSentencesByReleaseDate[0].sentenceCalculation.releaseDate
    val latestExpiryDate = mostRecentSentenceByExpiryDate.sentenceCalculation.expiryDate

    val latestUnadjustedExpiryDate: LocalDate = extractionService.mostRecent(
      sentences,
      SentenceCalculation::unadjustedExpiryDate,
    )

    val effectiveSentenceLength = getEffectiveSentenceLength(
      earliestSentenceDate,
      latestUnadjustedExpiryDate,
    )

    val latestLicenseExpirySentence: CalculableSentence? = extractionService.mostRecentSentenceOrNull(
      sentences,
      SentenceCalculation::licenceExpiryDate,
    )
    val latestLicenseExpiryDate: LocalDate? = latestLicenseExpirySentence?.sentenceCalculation?.licenceExpiryDate

    val latestNonParoleDate: LocalDate? = extractManyNonParoleDate(sentences, latestReleaseDate)

    val latestHDCEDAndBreakdown =
      hdcedExtractionService.extractManyHomeDetentionCurfewEligibilityDate(sentences, mostRecentSentencesByReleaseDate)

    val latestTUSEDAndBreakdown = if (latestLicenseExpiryDate != null) {
      extractManyTopUpSuperVisionDate(sentences, latestLicenseExpiryDate)
    } else if (isTusedableDtos(sentences, offender)) {
      val latestTUSEDSentence = sentences
        .filter { it.sentenceCalculation.topUpSupervisionDate != null }
        .maxByOrNull { it.sentenceCalculation.topUpSupervisionDate!! }
      latestTUSEDSentence?.sentenceCalculation?.topUpSupervisionDate!! to latestTUSEDSentence.sentenceCalculation.breakdownByReleaseDateType[TUSED]!!
    } else {
      null
    }

    val latestExtendedDeterminateParoleEligibilityDate: LocalDate? = extractionService.mostRecentOrNull(
      sentences,
      SentenceCalculation::extendedDeterminateParoleEligibilityDate,
    )

    val concurrentOraAndNonOraDetails = extractConcurrentOraAndNonOraDetails(
      mostRecentSentenceByAdjustedDeterminateReleaseDate.releaseDateTypes.getReleaseDateTypes(),
      mostRecentSentenceByExpiryDate.releaseDateTypes.getReleaseDateTypes(),
      latestReleaseDate,
      sentences,
      effectiveSentenceLength,
      offender,
    )

    val latestNotionalConditionalReleaseDate: LocalDate? = extractionService.mostRecentOrNull(
      sentences,
      SentenceCalculation::notionalConditionalReleaseDate,
    )

    if (latestExpiryDate == latestLicenseExpiryDate &&
      mostRecentSentenceByExpiryDate.releaseDateTypes.getReleaseDateTypes()
        .contains(SLED)
    ) {
      dates[SLED] = latestExpiryDate
      breakdownByReleaseDateType[SLED] =
        mostRecentSentenceByExpiryDate.sentenceCalculation.breakdownByReleaseDateType[SLED]!!
    } else if (sentences.any { it.isDto() } && !sentences.all { it.isDto() }) {
      val latestNonDtoSentence = sentences.sortedBy { it.sentenceCalculation.releaseDate }.last { !it.isDto() }
      val latestDtoSentence = sentences.sortedBy { it.sentenceCalculation.releaseDate }.last { it.isDto() }
      if (latestNonDtoSentence.sentenceCalculation.expiryDate.equals(latestDtoSentence.sentenceCalculation.expiryDate)) {
        dates[SLED] = latestExpiryDate
        breakdownByReleaseDateType[SLED] =
          mostRecentSentenceByExpiryDate.sentenceCalculation.breakdownByReleaseDateType[SED]!!
      } else {
        dates[SED] = latestExpiryDate
        breakdownByReleaseDateType[SED] =
          mostRecentSentenceByExpiryDate.sentenceCalculation.breakdownByReleaseDateType[SED]!!
      }
    } else {
      dates[SED] = latestExpiryDate
      breakdownByReleaseDateType[SED] =
        mostRecentSentenceByExpiryDate.sentenceCalculation.breakdownByReleaseDateType[SED]!!
      if (latestLicenseExpiryDate != null &&
        concurrentOraAndNonOraDetails.canHaveLicenseExpiry &&
        latestLicenseExpiryDate.isAfterOrEqualTo(
          latestReleaseDate,
        )
      ) {
        dates[LED] = latestLicenseExpiryDate
        if (latestLicenseExpirySentence.sentenceCalculation.breakdownByReleaseDateType.containsKey(LED)) {
          breakdownByReleaseDateType[LED] =
            latestLicenseExpirySentence.sentenceCalculation.breakdownByReleaseDateType[LED]!!
        } else if (latestLicenseExpirySentence.sentenceCalculation.breakdownByReleaseDateType.containsKey(SLED)) {
          breakdownByReleaseDateType[LED] =
            latestLicenseExpirySentence.sentenceCalculation.breakdownByReleaseDateType[SLED]!!
        }
      }
    }

    if (sentences.any { it.isDto() }) {
      if (sentences.all { it.isDto() }) {
        dates[MTD] = latestReleaseDate
        calculateWhenAllDtos(mostRecentSentenceByExpiryDate, dates)
      } else {
        val latestNonDtoSentence = sentences.sortedBy { it.sentenceCalculation.releaseDate }.last { !it.isDto() }
        val latestDtoSentence = sentences.sortedBy { it.sentenceCalculation.releaseDate }.last { it.isDto() }
        val type = if (concurrentOraAndNonOraDetails.isReleaseDateConditional) CRD else ARD
        dates[type] = latestNonDtoSentence.sentenceCalculation.releaseDate
        val midTermDate = calculateMidTermDate(
          latestDtoSentence,
          type,
          latestReleaseDate,
          sentences.all { offender.underEighteenAt(it.sentenceCalculation.releaseDate) },
        )
        dates[MTD] = midTermDate
        if (!sentences.any { it.sentenceCalculation.isImmediateRelease() && it.isDto() }) {
          calculateLtd(latestDtoSentence, dates)
          calculateEtd(latestDtoSentence, dates)
        }
        if (latestNonDtoSentence.sentenceCalculation.isReleaseDateConditional && dates[SLED] == null) {
          dates[LED] = latestNonDtoSentence.sentenceCalculation.licenceExpiryDate!!
        }
      }
    } else if (mostRecentSentencesByReleaseDate.any { !it.isRecall() }) {
      extractCrdOrArd(
        mostRecentSentencesByReleaseDate,
        concurrentOraAndNonOraDetails,
        dates,
        latestReleaseDate,
        breakdownByReleaseDateType,
      )
    }

    val latestPostRecallReleaseDate =
      extractionService.mostRecentOrNull(sentences, SentenceCalculation::adjustedPostRecallReleaseDate)
    if (latestPostRecallReleaseDate != null) {
      otherDates[PRRD] = latestPostRecallReleaseDate
    }

    if (latestNonParoleDate != null) {
      dates[NPD] = latestNonParoleDate
    }

    if (latestTUSEDAndBreakdown != null) {
      dates[TUSED] = latestTUSEDAndBreakdown.first
      breakdownByReleaseDateType[TUSED] = latestTUSEDAndBreakdown.second
    }

    if (latestHDCEDAndBreakdown != null) {
      dates[HDCED] = latestHDCEDAndBreakdown.first
      breakdownByReleaseDateType[HDCED] = latestHDCEDAndBreakdown.second
    }

    if (latestNotionalConditionalReleaseDate != null) {
      dates[NCRD] = latestNotionalConditionalReleaseDate
    }

    /** --- PED --- **/
    extractPedForBooking(
      latestExtendedDeterminateParoleEligibilityDate,
      mostRecentSentenceByAdjustedDeterminateReleaseDate,
      sentences,
      dates,
      breakdownByReleaseDateType,
    )

    /** --- ERSED --- **/
    val ersedNotApplicableDueToDtoLaterThanCrd = extractErsedAndNotApplicableDueToDtoLaterThanCrdFlag(
      sentences,
      breakdownByReleaseDateType,
      dates,
      sentenceGroups,
    )

    if (mostRecentSentencesByReleaseDate.any { it.isRecall() }) {
      dates[PRRD] = fixedTermRecallsService.calculatePRRD(
        sentences,
        latestExpiryDate,
        latestReleaseDate,
        returnToCustodyDate,
        dates[SLED],
      )

      if (!fixedTermRecallsService.hasHomeDetentionCurfew(dates)) {
        dates.remove(HDCED)
      }
    }

    /** --- ESED --- **/
    dates[ESED] = latestUnadjustedExpiryDate

    val sds40EligibleReleaseTypes = listOf(CRD, ARD, HDCED, TUSED, PED, ERSED)

    val isAnyRelevantSentenceAffectedBySds40 = sentences.any { sentence ->
      isAffectedBySds40(sentence) &&
        sds40EligibleReleaseTypes.any { type ->
          val targetDate = dates[type] // only include the eligible types of dates
          targetDate != null && sentence.sentenceCalculation.getDateByType(type) == targetDate
        }
    }

    return CalculationResult(
      dates.toMap(),
      breakdownByReleaseDateType.toMap(),
      otherDates.toMap(),
      effectiveSentenceLength,
      ersedNotApplicableDueToDtoLaterThanCrd,
      affectedBySds40 = isAnyRelevantSentenceAffectedBySds40,
    )
  }

  private fun isAffectedBySds40(sentence: CalculableSentence): Boolean = !sentence.isRecall() &&
    sentence.sentenceParts().any {
      it.identificationTrack == SentenceIdentificationTrack.SDS_EARLY_RELEASE ||
        it.identificationTrack == SentenceIdentificationTrack.SDS_STANDARD_RELEASE_T3_EXCLUSION
    } &&
    sentence.sentenceCalculation.adjustedDeterminateReleaseDate != sentence.sentenceCalculation.adjustedHistoricDeterminateReleaseDate

  fun extractCrdOrArd(
    mostRecentSentencesByReleaseDate: List<CalculableSentence>,
    concurrentOraAndNonOraDetails: ConcurrentOraAndNonOraDetails,
    dates: MutableMap<ReleaseDateType, LocalDate>,
    latestReleaseDate: LocalDate,
    breakdownByReleaseDateType: MutableMap<ReleaseDateType, ReleaseDateCalculationBreakdown>,
  ) {
    val mostRecentSentenceByReleaseDate = mostRecentSentencesByReleaseDate.first { !it.isRecall() }
    if (concurrentOraAndNonOraDetails.isReleaseDateConditional) {
      dates[CRD] = latestReleaseDate
      // PSI Example 16 results in a situation where the latest calculated sentence has ARD associated but isReleaseDateConditional here is deemed true.
      val releaseDateType =
        if (mostRecentSentenceByReleaseDate.sentenceCalculation.breakdownByReleaseDateType.containsKey(CRD)) CRD else ARD
      breakdownByReleaseDateType[CRD] =
        mostRecentSentenceByReleaseDate.sentenceCalculation.breakdownByReleaseDateType[releaseDateType]!!
    } else {
      dates[ARD] = latestReleaseDate
      breakdownByReleaseDateType[ARD] =
        mostRecentSentenceByReleaseDate.sentenceCalculation.breakdownByReleaseDateType[ARD]!!
    }
  }

  private fun extractPedForBooking(
    latestExtendedDeterminateParoleEligibilityDate: LocalDate?,
    mostRecentSentenceByAdjustedDeterminateReleaseDate: CalculableSentence,
    sentences: List<CalculableSentence>,
    dates: MutableMap<ReleaseDateType, LocalDate>,
    breakdownByReleaseDateType: MutableMap<ReleaseDateType, ReleaseDateCalculationBreakdown>,
  ) {
    if (latestExtendedDeterminateParoleEligibilityDate != null) {
      val mostRecentReleaseSentenceHasParoleDate =
        mostRecentSentenceByAdjustedDeterminateReleaseDate.sentenceCalculation.extendedDeterminateParoleEligibilityDate
      if (mostRecentReleaseSentenceHasParoleDate != null) {
        val latestNonPedReleaseSentence = extractionService.mostRecentSentenceOrNull(
          sentences.filter { !it.isRecall() && it.sentenceCalculation.extendedDeterminateParoleEligibilityDate == null && !it.isDto() && !it.sentenceCalculation.isImmediateRelease() },
          SentenceCalculation::releaseDate,
        )
        val latestNonPedRelease = latestNonPedReleaseSentence?.sentenceCalculation?.releaseDate
        if (latestNonPedRelease != null &&
          latestNonPedRelease.isAfterOrEqualTo(
            mostRecentSentenceByAdjustedDeterminateReleaseDate.sentenceCalculation.releaseDate,
          )
        ) {
          // SDS release is after PED, so no PED required.
        } else {
          if (latestNonPedRelease != null && latestExtendedDeterminateParoleEligibilityDate.isBefore(latestNonPedRelease)) {
            dates[PED] = latestNonPedRelease
            breakdownByReleaseDateType[PED] = ReleaseDateCalculationBreakdown(
              rules = setOf(
                if (latestNonPedReleaseSentence.releaseDateTypes.getReleaseDateTypes().contains(ARD)) {
                  CalculationRule.PED_EQUAL_TO_LATEST_NON_PED_ACTUAL_RELEASE
                } else {
                  CalculationRule.PED_EQUAL_TO_LATEST_NON_PED_CONDITIONAL_RELEASE
                },
              ),
              releaseDate = dates[PED]!!,
              unadjustedDate = latestExtendedDeterminateParoleEligibilityDate,
            )
          } else {
            dates[PED] = latestExtendedDeterminateParoleEligibilityDate
            breakdownByReleaseDateType[PED] = ReleaseDateCalculationBreakdown(
              releaseDate = dates[PED]!!,
              unadjustedDate = latestExtendedDeterminateParoleEligibilityDate,
            )
          }
        }
      }
    }
  }

  private fun extractErsedAndNotApplicableDueToDtoLaterThanCrdFlag(
    sentences: List<CalculableSentence>,
    breakdownByReleaseDateType: MutableMap<ReleaseDateType, ReleaseDateCalculationBreakdown>,
    dates: MutableMap<ReleaseDateType, LocalDate>,
    sentenceGroups: List<List<CalculableSentence>>,
  ): Boolean {
    val latestEarlyReleaseSchemeEligibilitySentence =
      extractionService.mostRecentSentenceOrNull(
        sentences,
        SentenceCalculation::earlyReleaseSchemeEligibilityDate,
      ) { !it.sentenceCalculation.isImmediateRelease() }

    if (latestEarlyReleaseSchemeEligibilitySentence != null) {
      val sentenceGroup = sentenceGroups.find { it.contains(latestEarlyReleaseSchemeEligibilitySentence) }!!
      val latestAFineRelease =
        extractionService.mostRecentSentenceOrNull(
          sentenceGroup.filter {
            it is AFineSentence &&
              !it.sentenceCalculation.isImmediateRelease() &&
              it.sentenceCalculation.releaseDate.isAfter(latestEarlyReleaseSchemeEligibilitySentence.sentenceCalculation.earlyReleaseSchemeEligibilityDate)
          },
          SentenceCalculation::releaseDate,
        )

      if (latestAFineRelease != null) {
        breakdownByReleaseDateType[ERSED] = ReleaseDateCalculationBreakdown(
          rules = setOf(CalculationRule.ERSED_ADJUSTED_TO_CONCURRENT_TERM),
          releaseDate = latestAFineRelease.sentenceCalculation.releaseDate,
          unadjustedDate = latestEarlyReleaseSchemeEligibilitySentence.sentenceCalculation.earlyReleaseSchemeEligibilityDate!!,
        )
        dates[ERSED] = latestAFineRelease.sentenceCalculation.releaseDate
        return false
      } else {
        if (sentences.any { it.isDto() }) {
          return calculateErsedWhereDtoIsPresent(
            dates,
            latestEarlyReleaseSchemeEligibilitySentence,
            breakdownByReleaseDateType,
          )
        } else {
          breakdownByReleaseDateType[ERSED] =
            latestEarlyReleaseSchemeEligibilitySentence.sentenceCalculation.breakdownByReleaseDateType[ERSED]!!
          dates[ERSED] =
            latestEarlyReleaseSchemeEligibilitySentence.sentenceCalculation.earlyReleaseSchemeEligibilityDate!!
          return false
        }
      }
    }
    return false
  }

  private fun isTusedableDtos(sentences: List<CalculableSentence>, offender: Offender): Boolean = sentences.all { it.isDto() } &&
    !sentences.all { offender.underEighteenAt(it.sentenceCalculation.releaseDate) } &&
    sentences.all { it.durationIsLessThan(2, ChronoUnit.YEARS) }

  private fun calculateErsedWhereDtoIsPresent(
    dates: MutableMap<ReleaseDateType, LocalDate>,
    latestEarlyReleaseSchemeEligibilitySentence: CalculableSentence,
    breakdownByReleaseDateType: MutableMap<ReleaseDateType, ReleaseDateCalculationBreakdown>,
  ): Boolean {
    val releaseDate = dates[CRD] ?: dates[ARD]
    if (dates[MTD]?.isBefore(releaseDate)!!) {
      val ersed = latestEarlyReleaseSchemeEligibilitySentence.sentenceCalculation.earlyReleaseSchemeEligibilityDate!!
      if (dates[MTD]?.isBefore(ersed)!!) {
        breakdownByReleaseDateType[ERSED] =
          latestEarlyReleaseSchemeEligibilitySentence.sentenceCalculation.breakdownByReleaseDateType[ERSED]!!
        dates[ERSED] =
          latestEarlyReleaseSchemeEligibilitySentence.sentenceCalculation.earlyReleaseSchemeEligibilityDate!!
      } else {
        breakdownByReleaseDateType[ERSED] = ReleaseDateCalculationBreakdown(
          rules = setOf(ERSED_ADJUSTED_TO_MTD),
          releaseDate = dates[MTD]!!,
          unadjustedDate = ersed,
        )
        dates[ERSED] = dates[MTD]!!
      }
      return false
    } else {
      return true
    }
  }

  private fun calculateMidTermDate(
    latestDtoSentence: CalculableSentence,
    type: ReleaseDateType,
    latestReleaseDate: LocalDate,
    underEighteenAtEndOfCustodialPeriod: Boolean,
  ) = if (latestDtoSentence.sentenceCalculation.isImmediateRelease() && latestDtoSentence.identificationTrack == SentenceIdentificationTrack.DTO_AFTER_PCSC) {
    latestDtoSentence.sentencedAt
  } else if (type == CRD && latestDtoSentence.identificationTrack == SentenceIdentificationTrack.DTO_BEFORE_PCSC) {
    latestDtoSentence.sentenceCalculation.unadjustedDeterminateReleaseDate
  } else if (type == CRD || underEighteenAtEndOfCustodialPeriod) {
    latestDtoSentence.sentenceCalculation.adjustedDeterminateReleaseDate
  } else {
    latestReleaseDate
  }

  private fun calculateWhenAllDtos(sentence: CalculableSentence, dates: MutableMap<ReleaseDateType, LocalDate>) {
    if (sentence.releaseDateTypes.contains(ETD)) {
      calculateEtd(sentence, dates)
    }

    if (sentence.releaseDateTypes.contains(LTD)) {
      calculateLtd(sentence, dates)
    }
  }

  private fun calculateLtd(sentence: CalculableSentence, dates: MutableMap<ReleaseDateType, LocalDate>) {
    if (sentence.durationIsGreaterThanOrEqualTo(8, MONTHS) && sentence.durationIsLessThan(18, MONTHS)) {
      dates[LTD] = dates[MTD]!!.plusMonths(1)
    } else if (sentence.durationIsGreaterThanOrEqualTo(18, MONTHS) && sentence.durationIsLessThanEqualTo(24, MONTHS)) {
      dates[LTD] = dates[MTD]!!.plusMonths(2)
    }
  }

  private fun calculateEtd(sentence: CalculableSentence, dates: MutableMap<ReleaseDateType, LocalDate>) {
    if (sentence.durationIsGreaterThanOrEqualTo(8, MONTHS) && sentence.durationIsLessThan(18, MONTHS)) {
      dates[ETD] = dates[MTD]!!.minusMonths(1)
    } else if (sentence.durationIsGreaterThanOrEqualTo(18, MONTHS) && sentence.durationIsLessThanEqualTo(24, MONTHS)) {
      dates[ETD] = dates[MTD]!!.minusMonths(2)
    }
  }

  private fun getEffectiveSentenceLength(start: LocalDate, end: LocalDate): Period = Period.between(start, end.plusDays(1))

  private fun extractManyNonParoleDate(
    sentences: List<CalculableSentence>,
    latestReleaseDate: LocalDate,
  ): LocalDate? {
    val mostRecentNonParoleDate = extractionService.mostRecentOrNull(
      sentences,
      SentenceCalculation::nonParoleDate,
    )
    return if (mostRecentNonParoleDate != null &&
      mostRecentNonParoleDate.isAfter(latestReleaseDate)
    ) {
      latestReleaseDate
    } else {
      null
    }
  }

  private fun extractManyTopUpSuperVisionDate(
    sentences: List<CalculableSentence>,
    latestLicenseExpiryDate: LocalDate,
  ): Pair<LocalDate, ReleaseDateCalculationBreakdown>? {
    val (historicTUSEDs, calculatedTUSEDs) = sentences.partition { it is BotusSentence }

    val latestTUSEDSentence = calculatedTUSEDs
      .filter { it.isCalculationInitialised() && it.sentenceCalculation.topUpSupervisionDate != null }
      .maxByOrNull { it.sentenceCalculation.topUpSupervisionDate!! }

    val latestHistoricTUSEDSentence = historicTUSEDs
      .filter { it.isCalculationInitialised() && it.sentenceCalculation.topUpSupervisionDate != null }
      .maxByOrNull { it.sentenceCalculation.topUpSupervisionDate!! }

    // Use the latest calculated TUSED, otherwise use historic TUSED
    val latestTUSED = latestTUSEDSentence ?: latestHistoricTUSEDSentence

    return latestTUSED?.let { sentence ->
      // if DTO has sentence expiry date greater than top-up date, exclude TUSED in place of the SED
      sentences
        .filter { it.isDto() && it.isCalculationInitialised() && it.releaseDateTypes.contains(SED) }
        .maxByOrNull { it.sentenceCalculation.expiryDate }
        ?.let {
          if (it.sentenceCalculation.expiryDate > sentence.sentenceCalculation.topUpSupervisionDate) {
            return null
          }
        }

      val topUpDate = sentence.sentenceCalculation.topUpSupervisionDate
      val breakdown = sentence.sentenceCalculation.breakdownByReleaseDateType[TUSED]

      // CRS-2260 Added breakdown != null check - this can be null in this BOTUS scenario via the full-validation endpoint (fullValidationFromBookingData)
      if (topUpDate != null && breakdown != null && topUpDate.isAfter(latestLicenseExpiryDate)) {
        topUpDate to breakdown
      } else {
        null
      }
    }
  }

  private fun extractConcurrentOraAndNonOraDetails(
    latestReleaseTypes: List<ReleaseDateType>,
    latestExpiryTypes: List<ReleaseDateType>,
    latestReleaseDate: LocalDate,
    sentences: List<CalculableSentence>,
    effectiveSentenceLength: Period,
    offender: Offender,
  ): ConcurrentOraAndNonOraDetails {
    // Do we have a mix of ora and non-ora sentences
    oraAndNoneOraExtraction(
      latestReleaseTypes,
      latestExpiryTypes,
      latestReleaseDate,
      sentences,
      effectiveSentenceLength,
    )?.let { oraAndNoneOra: ConcurrentOraAndNonOraDetails ->
      return oraAndNoneOra
    }

    if (sentences.any { it.isDto() }) {
      if (sentences.all { offender.underEighteenAt(it.sentenceCalculation.releaseDate) } &&
        effectiveSentenceLength.toTotalMonths() < 12
      ) {
        return ConcurrentOraAndNonOraDetails(isReleaseDateConditional = false, canHaveLicenseExpiry = true)
      }

      if (sentences.any { !it.isDto() } && sentences.all { !offender.underEighteenAt(it.sentenceCalculation.releaseDate) }) {
        return ConcurrentOraAndNonOraDetails(isReleaseDateConditional = true, canHaveLicenseExpiry = true)
      }
    }

    return ConcurrentOraAndNonOraDetails(
      sentences.any { it.sentenceCalculation.licenceExpiryDate?.isAfterOrEqualTo(latestReleaseDate) ?: false },
      canHaveLicenseExpiry = true,
    )
  }

  data class ConcurrentOraAndNonOraDetails(
    val isReleaseDateConditional: Boolean,
    val canHaveLicenseExpiry: Boolean,
  )

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}
