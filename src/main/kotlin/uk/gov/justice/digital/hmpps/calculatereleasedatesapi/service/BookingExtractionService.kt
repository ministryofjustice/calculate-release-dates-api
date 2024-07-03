package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.joda.time.DateTime
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.Hdced4Configuration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.HdcedConfiguration
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
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.HDCED4PLUS
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
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.BotusSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculableSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.DetentionAndTrainingOrderSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ReleaseDateCalculationBreakdown
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.StandardDeterminateSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.isAfterOrEqualTo
import java.time.LocalDate
import java.time.Period
import java.time.temporal.ChronoUnit
import java.time.temporal.ChronoUnit.MONTHS

@Service
class BookingExtractionService(
  val extractionService: SentencesExtractionService,
  val hdcedConfiguration: HdcedConfiguration,
  val hdced4Configuration: Hdced4Configuration,
) {

  fun extract(
    booking: Booking,
  ): CalculationResult {
    return when (booking.getAllExtractableSentences().size) {
      0 -> throw NoSentencesProvidedException("At least one sentence must be provided")
      1 -> extractSingle(booking)
      else -> {
        extractMultiple(booking)
      }
    }
  }

  private fun extractSingle(booking: Booking): CalculationResult {
    val dates: MutableMap<ReleaseDateType, LocalDate> = mutableMapOf()
    val sentence = booking.getAllExtractableSentences()[0]
    val sentenceCalculation = sentence.sentenceCalculation
    var historicalTusedSource: HistoricalTusedSource? = null

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

    if (DateTime.now().toDate().after(hdced4Configuration.hdc4CommencementDate)) {
      // After commencement date only produce HDCED using HDCED4 ruling.
      if (sentenceCalculation.homeDetentionCurfew4PlusEligibilityDate != null && !sentence.releaseDateTypes.contains(PED)) {
        dates[HDCED] = sentenceCalculation.homeDetentionCurfew4PlusEligibilityDate!!
      }
    } else {
      if (sentenceCalculation.homeDetentionCurfewEligibilityDate != null) {
        dates[HDCED] = sentenceCalculation.homeDetentionCurfewEligibilityDate!!
      }

      if (sentenceCalculation.homeDetentionCurfew4PlusEligibilityDate != null && !sentence.releaseDateTypes.contains(PED)) {
        dates[HDCED4PLUS] = sentenceCalculation.homeDetentionCurfew4PlusEligibilityDate!!
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

    if (sentence.isBotus()) {
      if (sentence is BotusSentence && sentence.latestTusedDate != null && sentenceCalculation.expiryDate.isBefore(
          sentence.latestTusedDate,
        )
      ) {
        dates[TUSED] = sentence.latestTusedDate!!
        historicalTusedSource = sentence.latestTusedSource!!
      }
    } else {
      dates[ESED] = sentenceCalculation.unadjustedExpiryDate
    }

    return CalculationResult(
      dates,
      sentenceCalculation.breakdownByReleaseDateType,
      emptyMap(),
      getEffectiveSentenceLength(sentence.sentencedAt, sentenceCalculation.unadjustedExpiryDate),
      false,
      historicalTusedSource,
    )
  }

  /**
   *  Method applies business logic for when there are multiple sentences in a booking and the impact each may have on one another
   */
  private fun extractMultiple(booking: Booking): CalculationResult {
    val dates: MutableMap<ReleaseDateType, LocalDate> = mutableMapOf()
    val otherDates: MutableMap<ReleaseDateType, LocalDate> = mutableMapOf()
    val breakdownByReleaseDateType: MutableMap<ReleaseDateType, ReleaseDateCalculationBreakdown> = mutableMapOf()
    val sentences = booking.getAllExtractableSentences()
    val earliestSentenceDate = sentences.minOf { it.sentencedAt }

    val mostRecentSentencesByReleaseDate =
      extractionService.mostRecentSentences(sentences, SentenceCalculation::releaseDate)
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
      extractManyHomeDetentionCurfewEligibilityDate(
        booking.sentenceGroups,
        sentences,
        mostRecentSentencesByReleaseDate,
      )

    val latestHDC4PLUSAndBreakdown =
      extractManyHomeDetentionCurfew4PlusEligibilityDate(sentences, mostRecentSentencesByReleaseDate)

    val latestTUSEDAndBreakdown = if (latestLicenseExpiryDate != null) {
      extractManyTopUpSuperVisionDate(sentences, latestLicenseExpiryDate)
    } else if (isTusedableDtos(booking, effectiveSentenceLength)) {
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
      booking,
    )

    val latestNotionalConditionalReleaseDate: LocalDate? = extractionService.mostRecentOrNull(
      sentences,
      SentenceCalculation::notionalConditionalReleaseDate,
    )

    if (latestExpiryDate == latestLicenseExpiryDate && mostRecentSentenceByExpiryDate.releaseDateTypes.getReleaseDateTypes()
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
      if (latestLicenseExpiryDate != null && concurrentOraAndNonOraDetails.canHaveLicenseExpiry && latestLicenseExpiryDate.isAfterOrEqualTo(
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

    if (mostRecentSentencesByReleaseDate.any { it.isRecall() }) {
      dates[PRRD] = latestReleaseDate
    }
    if (booking.sentences.any { it is DetentionAndTrainingOrderSentence }) {
      if (booking.sentences.all { it.isDto() }) {
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
          booking.underEighteenAtEndOfCustodialPeriod(),
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

    if (DateTime.now().toDate().after(hdced4Configuration.hdc4CommencementDate)) {
      // After commencement date only produce HDCED using HDCED4 ruling.
      if (latestHDC4PLUSAndBreakdown != null) {
        dates[HDCED] = latestHDC4PLUSAndBreakdown.first
        breakdownByReleaseDateType[HDCED] = latestHDC4PLUSAndBreakdown.second
      }
    } else {
      if (latestHDCEDAndBreakdown != null) {
        dates[HDCED] = latestHDCEDAndBreakdown.first
        breakdownByReleaseDateType[HDCED] = latestHDCEDAndBreakdown.second
      }
      if (latestHDC4PLUSAndBreakdown != null) {
        dates[HDCED4PLUS] = latestHDC4PLUSAndBreakdown.first
        breakdownByReleaseDateType[HDCED4PLUS] = latestHDC4PLUSAndBreakdown.second
      }
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
    val ersedNotApplicableDueToDtoLaterThanCrd = extractErsedAndNotApplicableDueToDtoLaterThanCrdFlag(sentences, breakdownByReleaseDateType, dates, booking.sentenceGroups)

    /** --- ESED --- **/
    dates[ESED] = latestUnadjustedExpiryDate
    return CalculationResult(
      dates.toMap(),
      breakdownByReleaseDateType.toMap(),
      otherDates.toMap(),
      effectiveSentenceLength,
      ersedNotApplicableDueToDtoLaterThanCrd,
    )
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
        if (latestNonPedRelease != null && latestNonPedRelease.isAfterOrEqualTo(
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

  private fun isTusedableDtos(booking: Booking, effectiveSentenceLength: Period): Boolean {
    return booking.sentences.all { it.isDto() } && effectiveSentenceLength.toTotalMonths() < 24 && !booking.underEighteenAtEndOfCustodialPeriod()
  }

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
  ) =
    if (latestDtoSentence.sentenceCalculation.isImmediateRelease() && latestDtoSentence.identificationTrack == SentenceIdentificationTrack.DTO_AFTER_PCSC) {
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

  private fun extractManyHomeDetentionCurfewEligibilityDate(
    sentenceGroups: List<List<CalculableSentence>>,
    sentences: List<CalculableSentence>,
    mostRecentSentencesByReleaseDate: List<CalculableSentence>,
  ): Pair<LocalDate, ReleaseDateCalculationBreakdown>? {
    val latestAdjustedReleaseDate = mostRecentSentencesByReleaseDate[0].sentenceCalculation.releaseDate
    val mostRecentReleaseIsPrrd =
      mostRecentSentencesByReleaseDate.any { it.releaseDateTypes.getReleaseDateTypes().contains(PRRD) }
    if (!mostRecentReleaseIsPrrd) {
      val latestNonRecallRelease = extractionService.mostRecentSentenceOrNull(
        sentences.filter { !it.isRecall() && !it.isDto() },
        SentenceCalculation::releaseDate,
      )
      if (latestNonRecallRelease?.sentenceCalculation?.homeDetentionCurfewEligibilityDate != null) {
        val sentenceGroup = sentenceGroups.find { it.contains(mostRecentSentencesByReleaseDate[0]) }!!
        val earliestSentenceDate = sentenceGroup.filter { !it.isRecall() }.minOf { it.sentencedAt }
        val latestUnadjustedExpiryDate =
          extractionService.mostRecent(
            sentenceGroup.filter { !it.isRecall() },
            SentenceCalculation::unadjustedExpiryDate,
          )

        if (latestUnadjustedExpiryDate.isBefore(
            earliestSentenceDate.plus(
              hdcedConfiguration.maximumSentenceLengthYears,
              ChronoUnit.YEARS,
            ),
          )
        ) {
          val hdcedSentence = extractionService.mostRecentSentenceOrNull(
            sentences.filter { !latestAdjustedReleaseDate.isBefore(it.sentencedAt.plusDays(hdcedConfiguration.minimumDaysOnHdc)) },
            SentenceCalculation::homeDetentionCurfewEligibilityDate,
          )
          val latestConflictingNonHdcCrd = getLatestConflictingNonHdcOrHdc4Sentence(
            sentenceGroup.filter { it.sentenceCalculation.homeDetentionCurfewEligibilityDate == null },
            hdcedSentence?.sentenceCalculation?.homeDetentionCurfewEligibilityDate,
            hdcedSentence,
          )

          if (hdcedSentence != null) {
            return if (latestConflictingNonHdcCrd.first != hdcedSentence &&
              latestConflictingNonHdcCrd.first != null &&
              latestConflictingNonHdcCrd.second != null
            ) {
              getReleaseDateCalculationBreakDownFromLatestConflictingSentence(
                latestConflictingNonHdcCrd,
                hdcedSentence.sentenceCalculation.homeDetentionCurfewEligibilityDate!!,
              )
            } else {
              hdcedSentence.sentenceCalculation.homeDetentionCurfewEligibilityDate!! to hdcedSentence.sentenceCalculation.breakdownByReleaseDateType[HDCED]!!
            }
          }
        }
      }
    }
    return null
  }

  private fun getReleaseDateCalculationBreakDownFromLatestConflictingSentence(
    latestConflictingSentence: Pair<CalculableSentence?, LocalDate?>,
    hdcedSentenceDate: LocalDate,
  ): Pair<LocalDate, ReleaseDateCalculationBreakdown> {
    val adjustedReleaseDate = latestConflictingSentence.second!!
    return adjustedReleaseDate to ReleaseDateCalculationBreakdown(
      rules = setOf(if (latestConflictingSentence.first!!.releaseDateTypes.contains(ARD)) CalculationRule.HDCED_ADJUSTED_TO_CONCURRENT_ACTUAL_RELEASE else CalculationRule.HDCED_ADJUSTED_TO_CONCURRENT_CONDITIONAL_RELEASE),
      releaseDate = adjustedReleaseDate,
      unadjustedDate = hdcedSentenceDate,
      adjustedDays = ChronoUnit.DAYS.between(adjustedReleaseDate, hdcedSentenceDate).toInt(),
    )
  }

  private fun getLatestConflictingNonHdcOrHdc4Sentence(
    sentenceGroup: List<CalculableSentence>,
    calculatedHDCED: LocalDate?,
    hdcSentence: CalculableSentence?,
  ): Pair<CalculableSentence?, LocalDate?> {
    val otherNonSentencesInGroup =
      sentenceGroup.filter {
        it != hdcSentence &&
          it.sentenceCalculation.releaseDate.isAfter(calculatedHDCED) &&
          !it.isDto() &&
          !it.releaseDateTypes.contains(PRRD)
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

  private fun extractManyHomeDetentionCurfew4PlusEligibilityDate(
    sentences: List<CalculableSentence>,
    mostRecentSentencesByReleaseDate: List<CalculableSentence>,
  ): Pair<LocalDate, ReleaseDateCalculationBreakdown>? {
    val latestAdjustedReleaseDate = mostRecentSentencesByReleaseDate[0].sentenceCalculation.releaseDate
    val mostRecentReleaseIsPrrd =
      mostRecentSentencesByReleaseDate.any { it.releaseDateTypes.getReleaseDateTypes().contains(PRRD) }

    // CRS-2032 - do not generate a HDCED for any booking that has an SDS+ sentence
    if (!mostRecentReleaseIsPrrd && !sentences.any { it.isSDSPlus }) {
      val latestNonRecallRelease = extractionService.mostRecentSentenceOrNull(
        sentences.filter { !it.isRecall() && !it.isDto() },
        SentenceCalculation::releaseDate,
      )
      if (latestNonRecallRelease?.sentenceCalculation?.homeDetentionCurfew4PlusEligibilityDate != null) {
        val hdcedSentence = extractionService.mostRecentSentenceOrNull(
          sentences.filter { !latestAdjustedReleaseDate.isBefore(it.sentencedAt.plusDays(14)) },
          SentenceCalculation::homeDetentionCurfew4PlusEligibilityDate,
        )
        val latestConflictingNonHdcCrd = getLatestConflictingNonHdcOrHdc4Sentence(
          sentences.filter { it.sentenceCalculation.homeDetentionCurfew4PlusEligibilityDate == null },
          hdcedSentence?.sentenceCalculation?.homeDetentionCurfew4PlusEligibilityDate,
          hdcedSentence,
        )

        if (hdcedSentence != null) {
          return if (hdcedSentence != latestConflictingNonHdcCrd.first &&
            latestConflictingNonHdcCrd.first != null &&
            latestConflictingNonHdcCrd.second != null
          ) {
            getReleaseDateCalculationBreakDownFromLatestConflictingSentence(
              latestConflictingNonHdcCrd,
              hdcedSentence.sentenceCalculation.homeDetentionCurfew4PlusEligibilityDate!!,
            )
          } else {
            hdcedSentence.sentenceCalculation.homeDetentionCurfew4PlusEligibilityDate!! to hdcedSentence.sentenceCalculation.breakdownByReleaseDateType[HDCED4PLUS]!!
          }
        }
      }
    }
    return null
  }

  private fun getEffectiveSentenceLength(start: LocalDate, end: LocalDate): Period =
    Period.between(start, end.plusDays(1))

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
    val latestTUSEDSentence = sentences
      .filter { it.sentenceCalculation.topUpSupervisionDate != null }
      .maxByOrNull { it.sentenceCalculation.topUpSupervisionDate!! }

    return if (latestTUSEDSentence != null && latestTUSEDSentence.sentenceCalculation.topUpSupervisionDate!!.isAfter(
        latestLicenseExpiryDate,
      )
    ) {
      latestTUSEDSentence.sentenceCalculation.topUpSupervisionDate!! to latestTUSEDSentence.sentenceCalculation.breakdownByReleaseDateType[TUSED]!!
    } else {
      null
    }
  }

  private fun extractConcurrentOraAndNonOraDetails(
    latestReleaseTypes: List<ReleaseDateType>,
    latestExpiryTypes: List<ReleaseDateType>,
    latestReleaseDate: LocalDate,
    sentences: List<CalculableSentence>,
    effectiveSentenceLength: Period,
    booking: Booking,
  ): ConcurrentOraAndNonOraDetails {
    val latestReleaseIsConditional = latestReleaseTypes.contains(CRD)
    val latestSentenceExpiryIsSED = latestExpiryTypes.contains(SED)

    val hasOraSentences = sentences.any { (it is StandardDeterminateSentence) && it.isOraSentence() }
    val hasNonOraSentencesOfLessThan12Months =
      sentences.any { (it is StandardDeterminateSentence) && !it.isOraSentence() && it.durationIsLessThan(12, MONTHS) }
    val mostRecentSentenceWithASed = extractionService.mostRecentSentenceOrNull(
      sentences,
      SentenceCalculation::expiryDate,
    ) { it.releaseDateTypes.contains(SED) }
    val mostRecentSentenceWithASled = extractionService.mostRecentSentenceOrNull(
      sentences,
      SentenceCalculation::expiryDate,
    ) { it.releaseDateTypes.contains(SLED) }

    // We have a mix of ora and non-ora sentences
    if (hasOraSentences && hasNonOraSentencesOfLessThan12Months && mostRecentSentenceWithASed != null && mostRecentSentenceWithASled != null && effectiveSentenceLength.years < FOUR) {
      if (!latestReleaseIsConditional) {
        if (latestSentenceExpiryIsSED) {
          if (latestReleaseDate.isAfter(mostRecentSentenceWithASled.sentenceCalculation.expiryDate)) {
            return ConcurrentOraAndNonOraDetails(
              isReleaseDateConditional = false,
              canHaveLicenseExpiry = false,
            )
          } else {
            return ConcurrentOraAndNonOraDetails(
              isReleaseDateConditional = true,
              canHaveLicenseExpiry = true,
            )
          }
        } else {
          return ConcurrentOraAndNonOraDetails(
            isReleaseDateConditional = true,
            canHaveLicenseExpiry = true,
          )
        }
      }
    }
    val hasLicence = sentences.any {
      it.sentenceCalculation.licenceExpiryDate != null && it.sentenceCalculation.licenceExpiryDate!!.isAfterOrEqualTo(
        latestReleaseDate,
      )
    }

    if ((sentences.any { it.isDto() })) {
      if (booking.underEighteenAtEndOfCustodialPeriod() && effectiveSentenceLength.toTotalMonths() < 12) {
        return ConcurrentOraAndNonOraDetails(isReleaseDateConditional = false, canHaveLicenseExpiry = true)
      }
      if (sentences.any { !it.isDto() } && !booking.underEighteenAtEndOfCustodialPeriod()) {
        return ConcurrentOraAndNonOraDetails(isReleaseDateConditional = true, canHaveLicenseExpiry = true)
      }
    }

    return ConcurrentOraAndNonOraDetails(
      hasLicence,
      canHaveLicenseExpiry = true,
    )
  }

  data class ConcurrentOraAndNonOraDetails(
    val isReleaseDateConditional: Boolean,
    val canHaveLicenseExpiry: Boolean,
  )

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
    private const val FOUR = 4L
    private const val INT_EIGHTEEN = 18
  }
}
