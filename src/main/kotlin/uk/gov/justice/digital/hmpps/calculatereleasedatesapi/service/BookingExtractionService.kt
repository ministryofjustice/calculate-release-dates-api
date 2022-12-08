package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationRule
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.ARD
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.CRD
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.ERSED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.ESED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.HDCED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.LED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.NCRD
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.NPD
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.PED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.PRRD
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.SED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.SLED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.TUSED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.NoSentencesProvidedException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AFineSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculableSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ConsecutiveSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ReleaseDateCalculationBreakdown
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.StandardDeterminateSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.isAfterOrEqualTo
import java.time.LocalDate
import java.time.Period
import java.time.temporal.ChronoUnit

@Service
class BookingExtractionService(
  val extractionService: SentencesExtractionService
) {

  fun extract(
    booking: Booking
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

    if (sentence.releaseDateTypes.contains(SLED)) {
      dates[SLED] = sentenceCalculation.expiryDate!!
    } else {
      dates[SED] = sentenceCalculation.expiryDate!!
    }

    dates[sentence.getReleaseDateType()] = sentenceCalculation.releaseDate

    if (sentenceCalculation.licenceExpiryDate != null &&
      sentence.releaseDateTypes.contains(LED)
    ) {
      dates[LED] = sentenceCalculation.licenceExpiryDate!!
    }

    if (sentenceCalculation.nonParoleDate != null) {
      dates[NPD] = sentenceCalculation.nonParoleDate!!
    }

    if (sentenceCalculation.topUpSupervisionDate != null) {
      dates[TUSED] = sentenceCalculation.topUpSupervisionDate!!
    }

    if (sentenceCalculation.homeDetentionCurfewEligibilityDate != null) {
      dates[HDCED] = sentenceCalculation.homeDetentionCurfewEligibilityDate!!
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

    dates[ESED] = sentenceCalculation.unadjustedExpiryDate

    return CalculationResult(
      dates, sentenceCalculation.breakdownByReleaseDateType,
      emptyMap(),
      getEffectiveSentenceLength(sentence.sentencedAt, sentenceCalculation.unadjustedExpiryDate)
    )
  }

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
    val latestExpiryDate = mostRecentSentenceByExpiryDate.sentenceCalculation.expiryDate!!

    val latestUnadjustedExpiryDate: LocalDate = extractionService.mostRecent(
      sentences, SentenceCalculation::unadjustedExpiryDate
    )

    val effectiveSentenceLength = getEffectiveSentenceLength(
      earliestSentenceDate,
      latestUnadjustedExpiryDate
    )

    val latestLicenseExpirySentence: CalculableSentence? = extractionService.mostRecentSentenceOrNull(
      sentences, SentenceCalculation::licenceExpiryDate
    )
    val latestLicenseExpiryDate: LocalDate? = latestLicenseExpirySentence?.sentenceCalculation?.licenceExpiryDate

    val latestNonParoleDate: LocalDate? = extractManyNonParoleDate(sentences, latestReleaseDate)

    val latestHDCEDAndBreakdown =
      extractManyHomeDetentionCurfewEligibilityDate(
        sentences,
        mostRecentSentencesByReleaseDate
      )

    val latestTUSEDAndBreakdown = if (latestLicenseExpiryDate != null) {
      extractManyTopUpSuperVisionDate(sentences, latestLicenseExpiryDate)
    } else {
      null
    }

    val latestExtendedDeterminateParoleEligibilityDate: LocalDate? = extractionService.mostRecentOrNull(
      sentences, SentenceCalculation::extendedDeterminateParoleEligibilityDate
    )

    val concurrentOraAndNonOraDetails = extractConcurrentOraAndNonOraDetails(
      mostRecentSentenceByAdjustedDeterminateReleaseDate.releaseDateTypes, mostRecentSentenceByExpiryDate.releaseDateTypes, latestReleaseDate, sentences, effectiveSentenceLength
    )

    val latestNotionalConditionalReleaseDate: LocalDate? = extractionService.mostRecentOrNull(
      sentences, SentenceCalculation::notionalConditionalReleaseDate
    )

    if (latestExpiryDate == latestLicenseExpiryDate && mostRecentSentenceByExpiryDate.releaseDateTypes.contains(SLED)) {
      dates[SLED] = latestExpiryDate
      breakdownByReleaseDateType[SLED] =
        mostRecentSentenceByExpiryDate.sentenceCalculation.breakdownByReleaseDateType[SLED]!!
    } else {
      dates[SED] = latestExpiryDate
      breakdownByReleaseDateType[SED] =
        mostRecentSentenceByExpiryDate.sentenceCalculation.breakdownByReleaseDateType[SED]!!
      if (latestLicenseExpiryDate != null && concurrentOraAndNonOraDetails.canHaveLicenseExpiry && latestLicenseExpiryDate.isAfterOrEqualTo(latestReleaseDate)) {
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
    if (mostRecentSentencesByReleaseDate.any { !it.isRecall() }) {
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

    val latestPostRecallReleaseDate = extractionService.mostRecentOrNull(sentences, SentenceCalculation::adjustedPostRecallReleaseDate)
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

    if (latestExtendedDeterminateParoleEligibilityDate != null) {
      val mostRecentReleaseSentenceHasParoleDate = mostRecentSentenceByAdjustedDeterminateReleaseDate.sentenceCalculation.extendedDeterminateParoleEligibilityDate
      if (mostRecentReleaseSentenceHasParoleDate != null) {
        val latestNonPedReleaseSentence = extractionService.mostRecentSentenceOrNull(
          sentences.filter { !it.isRecall() && it.sentenceCalculation.extendedDeterminateParoleEligibilityDate == null },
          SentenceCalculation::releaseDate
        )
        val latestNonPedRelease = latestNonPedReleaseSentence?.sentenceCalculation?.releaseDate
        if (latestNonPedRelease != null && latestNonPedRelease.isAfterOrEqualTo(mostRecentSentenceByAdjustedDeterminateReleaseDate.sentenceCalculation.releaseDate)) {
          // SDS release is after PED, so no PED required.
        } else {
          if (latestNonPedRelease != null && latestExtendedDeterminateParoleEligibilityDate.isBefore(latestNonPedRelease)) {
            dates[PED] = latestNonPedRelease
            breakdownByReleaseDateType[PED] = ReleaseDateCalculationBreakdown(
              rules = setOf(if (latestNonPedReleaseSentence.releaseDateTypes.contains(ARD)) CalculationRule.PED_EQUAL_TO_LATEST_NON_PED_ACTUAL_RELEASE else CalculationRule.PED_EQUAL_TO_LATEST_NON_PED_CONDITIONAL_RELEASE),
              releaseDate = dates[PED]!!,
              unadjustedDate = latestExtendedDeterminateParoleEligibilityDate
            )
          } else {
            dates[PED] = latestExtendedDeterminateParoleEligibilityDate
            breakdownByReleaseDateType[PED] = ReleaseDateCalculationBreakdown(
              releaseDate = dates[PED]!!,
              unadjustedDate = latestExtendedDeterminateParoleEligibilityDate
            )
          }
        }
      }
    }

    val latestEarlyReleaseSchemeEligibilityDate = extractionService.mostRecentOrNull(sentences, SentenceCalculation::earlyReleaseSchemeEligibilityDate)
    val latestAFineRelease = extractionService.mostRecentOrNull(sentences.filterIsInstance<AFineSentence>(), SentenceCalculation::releaseDate)
    val afineIsRelease = latestAFineRelease == latestReleaseDate
    if (latestEarlyReleaseSchemeEligibilityDate != null && !afineIsRelease) {
      dates[ERSED] = if (latestAFineRelease != null && latestEarlyReleaseSchemeEligibilityDate.isBefore(latestAFineRelease)) latestAFineRelease else latestEarlyReleaseSchemeEligibilityDate
    }

    dates[ESED] = latestUnadjustedExpiryDate
    return CalculationResult(dates.toMap(), breakdownByReleaseDateType.toMap(), otherDates.toMap(), effectiveSentenceLength)
  }

  private fun extractManyHomeDetentionCurfewEligibilityDate(
    sentences: List<CalculableSentence>,
    mostRecentSentencesByReleaseDate: List<CalculableSentence>
  ): Pair<LocalDate, ReleaseDateCalculationBreakdown>? {
    val latestAdjustedReleaseDate = mostRecentSentencesByReleaseDate[0].sentenceCalculation.releaseDate
    val mostRecentReleaseIsPrrd = mostRecentSentencesByReleaseDate.any { it.releaseDateTypes.contains(PRRD) }
    // For now we can't calculate HDCED if there is a consecutive sentence with EDS or SOPC sentences
    if (!mostRecentReleaseIsPrrd && sentences.none { it is ConsecutiveSentence && it.hasAnyEdsOrSopcSentence() }) {
      val latestNonRecallRelease = extractionService.mostRecentSentenceOrNull(sentences.filter { !it.isRecall() }, SentenceCalculation::releaseDate)
      if (latestNonRecallRelease?.sentenceCalculation?.homeDetentionCurfewEligibilityDate != null) {
        val earliestSentenceDate = sentences.filter { !it.isRecall() }.minOf { it.sentencedAt }
        val latestUnadjustedExpiryDate =
          extractionService.mostRecent(sentences.filter { !it.isRecall() }, SentenceCalculation::unadjustedExpiryDate)
        val effectiveSentenceLength = getEffectiveSentenceLength(
          earliestSentenceDate,
          latestUnadjustedExpiryDate
        )
        if (effectiveSentenceLength.years < 4) {
          val hdcedSentence = extractionService.mostRecentSentenceOrNull(
            sentences.filter { !latestAdjustedReleaseDate.isBefore(it.sentencedAt.plusDays(14)) },
            SentenceCalculation::homeDetentionCurfewEligibilityDate
          )
          val latestSopcOrEdsRelease = extractionService.mostRecentSentenceOrNull(sentences.filter { it.hasAnyEdsOrSopcSentence() && !it.sentenceCalculation.isImmediateRelease() }, SentenceCalculation::releaseDate)
          val latestAFineRelease = extractionService.mostRecentSentenceOrNull(sentences.filter { it is AFineSentence && !it.sentenceCalculation.isImmediateRelease() }, SentenceCalculation::releaseDate)
          val latestSdsArdRelease = extractionService.mostRecentSentenceOrNull(sentences.filter { it.releaseDateTypes.contains(ARD) && !it.sentenceCalculation.isImmediateRelease() && it.sentenceCalculation.homeDetentionCurfewEligibilityDate == null }, SentenceCalculation::releaseDate)
          val latestConcurrentReleaseSentence = listOfNotNull(latestSopcOrEdsRelease, latestAFineRelease, latestSdsArdRelease).maxByOrNull { it.sentenceCalculation.releaseDate }
          val latestConcurrentRelease = latestConcurrentReleaseSentence?.sentenceCalculation?.releaseDate
          if (hdcedSentence != null) {
            return if (latestConcurrentRelease != null && hdcedSentence.sentenceCalculation.homeDetentionCurfewEligibilityDate!!.isBefore(latestConcurrentRelease)) {
              latestConcurrentRelease to ReleaseDateCalculationBreakdown(
                rules = setOf(if (latestConcurrentReleaseSentence.releaseDateTypes.contains(ARD)) CalculationRule.HDCED_ADJUSTED_TO_CONCURRENT_ACTUAL_RELEASE else CalculationRule.HDCED_ADJUSTED_TO_CONCURRENT_CONDITIONAL_RELEASE),
                releaseDate = latestConcurrentRelease,
                unadjustedDate = hdcedSentence.sentenceCalculation.homeDetentionCurfewEligibilityDate!!,
                adjustedDays = ChronoUnit.DAYS.between(
                  latestConcurrentRelease,
                  hdcedSentence.sentenceCalculation.homeDetentionCurfewEligibilityDate!!
                ).toInt()
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

  private fun getEffectiveSentenceLength(start: LocalDate, end: LocalDate): Period =
    Period.between(start, end.plusDays(1))

  private fun extractManyNonParoleDate(
    sentences: List<CalculableSentence>,
    latestReleaseDate: LocalDate
  ): LocalDate? {

    val mostRecentNonParoleDate = extractionService.mostRecentOrNull(
      sentences, SentenceCalculation::nonParoleDate
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
    latestLicenseExpiryDate: LocalDate
  ): Pair<LocalDate, ReleaseDateCalculationBreakdown>? {
    val latestTUSEDSentence = sentences
      .filter { it.sentenceCalculation.topUpSupervisionDate != null }
      .maxByOrNull { it.sentenceCalculation.topUpSupervisionDate!! }

    return if (latestTUSEDSentence != null && latestTUSEDSentence.sentenceCalculation.topUpSupervisionDate!!.isAfter(latestLicenseExpiryDate)) {
      latestTUSEDSentence.sentenceCalculation.topUpSupervisionDate!! to latestTUSEDSentence.sentenceCalculation.breakdownByReleaseDateType[TUSED]!!
    } else null
  }

  private fun extractConcurrentOraAndNonOraDetails(
    latestReleaseTypes: List<ReleaseDateType>,
    latestExpiryTypes: List<ReleaseDateType>,
    latestReleaseDate: LocalDate,
    sentences: List<CalculableSentence>,
    effectiveSentenceLength: Period
  ): ConcurrentOraAndNonOraDetails {
    val latestReleaseIsConditional = latestReleaseTypes.contains(CRD)
    val latestSentenceExpiryIsSED = latestExpiryTypes.contains(SED)

    val hasOraSentences = sentences.any { (it is StandardDeterminateSentence) && it.isOraSentence() }
    val hasNonOraSentencesOfLessThan12Months = sentences.any { (it is StandardDeterminateSentence) && !it.isOraSentence() && it.durationIsLessThan(12, ChronoUnit.MONTHS) }
    val mostRecentSentenceWithASed = extractionService.mostRecentSentenceOrNull(
      sentences, SentenceCalculation::expiryDate
    ) { it.releaseDateTypes.contains(SED) }
    val mostRecentSentenceWithASled = extractionService.mostRecentSentenceOrNull(
      sentences, SentenceCalculation::expiryDate
    ) { it.releaseDateTypes.contains(SLED) }

    // We have a mix of ora and non-ora sentences
    if (hasOraSentences && hasNonOraSentencesOfLessThan12Months && mostRecentSentenceWithASed != null && mostRecentSentenceWithASled != null && effectiveSentenceLength.years < FOUR) {
      if (!latestReleaseIsConditional) {
        if (latestSentenceExpiryIsSED) {
          if (latestReleaseDate.isAfter(mostRecentSentenceWithASled.sentenceCalculation.expiryDate)) {
            return ConcurrentOraAndNonOraDetails(
              isReleaseDateConditional = false,
              canHaveLicenseExpiry = false
            )
          } else {
            return ConcurrentOraAndNonOraDetails(
              isReleaseDateConditional = true,
              canHaveLicenseExpiry = true
            )
          }
        } else {
          return ConcurrentOraAndNonOraDetails(
            isReleaseDateConditional = true,
            canHaveLicenseExpiry = true
          )
        }
      }
    }
    val hasLicence = sentences.any() { it.sentenceCalculation.licenceExpiryDate != null && it.sentenceCalculation.licenceExpiryDate!!.isAfterOrEqualTo(latestReleaseDate) }

    return ConcurrentOraAndNonOraDetails(
      hasLicence,
      canHaveLicenseExpiry = true
    )
  }

  data class ConcurrentOraAndNonOraDetails(
    val isReleaseDateConditional: Boolean,
    val canHaveLicenseExpiry: Boolean
  )

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
    private const val FOUR = 4L
  }
}
