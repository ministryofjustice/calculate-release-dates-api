package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.SDS40TrancheConfiguration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationRule
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SDSEarlyReleaseTranche
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceIdentificationTrack
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ConsecutiveSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ReleaseDateCalculationBreakdown
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.StandardDeterminateSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.isAfterOrEqualTo
import java.time.LocalDate

@Service
class SDSEarlyReleaseDefaultingRulesService(
  val extractionService: SentencesExtractionService,
  val trancheConfiguration: SDS40TrancheConfiguration,
) {
  fun applySDSEarlyReleaseRulesAndFinalizeDates(
    earlyReleaseResult: CalculationResult,
    standardReleaseResult: CalculationResult,
    trancheCommencementDate: LocalDate?,
    allocatedTranche: SDSEarlyReleaseTranche,
    originalBooking: Booking,
  ): CalculationResult {
    val dates = earlyReleaseResult.dates.toMutableMap()
    val breakdownByReleaseDateType = earlyReleaseResult.breakdownByReleaseDateType.toMutableMap()
    if (hasAnyReleaseBeforeTrancheCommencement(earlyReleaseResult, standardReleaseResult, trancheCommencementDate)) {
      DATE_TYPES_TO_ADJUST_TO_COMMENCEMENT_DATE.forEach { releaseDateType ->
        mergeDate(
          releaseDateType,
          earlyReleaseResult,
          standardReleaseResult,
          allocatedTranche,
          trancheCommencementDate,
          dates,
          breakdownByReleaseDateType,
        )
      }
    } else if (allocatedTranche != SDSEarlyReleaseTranche.TRANCHE_0) {
      DATE_TYPES_TO_ADJUST_TO_COMMENCEMENT_DATE.forEach { releaseDateType ->
        applyReleaseRules(
          releaseDateType,
          earlyReleaseResult,
          standardReleaseResult,
          trancheCommencementDate,
          breakdownByReleaseDateType,
        )
      }
    }

    handleTUSEDForSDSRecallsBeforeTrancheOneCommencement(
      dates,
      originalBooking,
      standardReleaseResult,
      breakdownByReleaseDateType,
    )

    handleCRDorARDAndPRRD(dates, earlyReleaseResult.otherDates.toMutableMap(), breakdownByReleaseDateType, standardReleaseResult)

    handleCRDEqualsEligibilityDateAndTrancheDate(dates)

    return CalculationResult(
      dates,
      breakdownByReleaseDateType,
      earlyReleaseResult.otherDates,
      earlyReleaseResult.effectiveSentenceLength,
      sdsEarlyReleaseAllocatedTranche = allocatedTranche,
      sdsEarlyReleaseTranche = allocatedTranche,
      affectedBySds40 = (dates != standardReleaseResult.dates),
    )
  }

  private fun getControllingDate(
    dates: MutableMap<ReleaseDateType, LocalDate>,
  ): Pair<ReleaseDateType, LocalDate?> {
    return if (dates[ReleaseDateType.ARD] != null) ReleaseDateType.ARD to dates[ReleaseDateType.ARD] else ReleaseDateType.CRD to dates[ReleaseDateType.CRD]
  }

  private fun handleCRDorARDAndPRRD(
    dates: MutableMap<ReleaseDateType, LocalDate>,
    otherDates: MutableMap<ReleaseDateType, LocalDate>,
    breakdownByReleaseDateType: MutableMap<ReleaseDateType, ReleaseDateCalculationBreakdown>,
    standardReleaseResult: CalculationResult,
  ) {
    if (dates.containsKey(ReleaseDateType.CRD) || dates.containsKey(ReleaseDateType.ARD)) {
      val controllingDate = getControllingDate(dates)

      dates[ReleaseDateType.HDCED]?.let { hdcedDate ->
        val prrdDate = dates[ReleaseDateType.PRRD] ?: otherDates[ReleaseDateType.PRRD]
        prrdDate?.takeIf { it.isAfter(hdcedDate) }?.let {
          setHDCEDDates(it, dates)
          standardReleaseResult.breakdownByReleaseDateType[ReleaseDateType.HDCED]?.apply {
            breakdownByReleaseDateType[ReleaseDateType.HDCED] = copy(
              rules = rules + CalculationRule.HDCED_ADJUSTED_TO_CONCURRENT_PRRD,
            )
          }
        }
      }

      if (dates.containsKey(ReleaseDateType.PRRD)) {
        if (controllingDate.second?.isAfter(dates[ReleaseDateType.PRRD]) == true) {
          dates.remove(ReleaseDateType.PRRD)
        } else {
          dates.remove(controllingDate.first)

          // PRRD is later than any other release date, therefore can not have a HDCED
          // See BookingExtractionService#extractMultiple
          removeHDCEDDates(dates)
        }
      }
    }
  }

  private fun setHDCEDDates(date: LocalDate, dates: MutableMap<ReleaseDateType, LocalDate>) {
    dates[ReleaseDateType.HDCED] = date
  }

  private fun removeHDCEDDates(dates: MutableMap<ReleaseDateType, LocalDate>) {
    dates.remove(ReleaseDateType.HDCED)
  }

  fun handleCRDEqualsEligibilityDateAndTrancheDate(dates: MutableMap<ReleaseDateType, LocalDate>) {
    if (dates.containsKey(ReleaseDateType.CRD) || dates.containsKey(ReleaseDateType.ARD)) {
      val controllingDate = getControllingDate(dates)

      val eligibilityDates = listOf(
        ReleaseDateType.HDCED,
        ReleaseDateType.ERSED,
      )

      eligibilityDates.forEach { dateType ->
        if (dates[dateType] == controllingDate.second &&
          (dates[dateType] == trancheConfiguration.trancheOneCommencementDate || dates[dateType] == trancheConfiguration.trancheTwoCommencementDate)
        ) {
          dates.remove(dateType)
        }
      }
    }
  }

  fun handleTUSEDForSDSRecallsBeforeTrancheOneCommencement(
    dates: MutableMap<ReleaseDateType, LocalDate>,
    originalBooking: Booking,
    standardReleaseResult: CalculationResult,
    breakdownByReleaseDateType: MutableMap<ReleaseDateType, ReleaseDateCalculationBreakdown>,
  ) {
    val latestReleaseDate = extractionService.mostRecentSentence(
      originalBooking.getAllExtractableSentences(),
      SentenceCalculation::adjustedDeterminateReleaseDate,
    ).sentenceCalculation.adjustedDeterminateReleaseDate

    if (dates.containsKey(ReleaseDateType.TUSED) && !latestReleaseDate.isAfterOrEqualTo(trancheConfiguration.trancheOneCommencementDate)) {
      if (hasEligibleSentenceForTUSED(originalBooking)) {
        standardReleaseResult.dates[ReleaseDateType.TUSED]?.let {
          dates[ReleaseDateType.TUSED] = it
        }
        standardReleaseResult.breakdownByReleaseDateType[ReleaseDateType.TUSED]?.let {
          breakdownByReleaseDateType[ReleaseDateType.TUSED] = it
        }
      }
    }
  }

  private fun hasEligibleSentenceForTUSED(booking: Booking): Boolean {
    return booking.getAllExtractableSentences().any { sentence ->
      sentence.releaseDateTypes.contains(ReleaseDateType.TUSED) &&
        sentence.recallType != null &&
        when (sentence) {
          is StandardDeterminateSentence -> true
          is ConsecutiveSentence -> hasStandardDeterminateSentence(sentence)
          else -> false
        }
    }
  }
  private fun hasStandardDeterminateSentence(sentence: ConsecutiveSentence): Boolean = sentence.orderedSentences.any { it is StandardDeterminateSentence }

  fun hasAnySDSEarlyRelease(anInitialCalc: Booking) =
    anInitialCalc.sentences.any { it.identificationTrack == SentenceIdentificationTrack.SDS_EARLY_RELEASE }

  fun hasAnyReleaseBeforeTrancheCommencement(
    early: CalculationResult,
    late: CalculationResult,
    trancheCommencementDate: LocalDate?,
  ): Boolean {
    if (trancheCommencementDate == null) {
      return false
    }

    return DATE_TYPES_TO_ADJUST_TO_COMMENCEMENT_DATE
      .mapNotNull { dateType ->
        early.dates[dateType] ?: late.dates[dateType]
      }
      .any { it < trancheCommencementDate }
  }

  private fun mergeDate(
    dateType: ReleaseDateType,
    earlyReleaseResult: CalculationResult,
    standardReleaseResult: CalculationResult,
    allocatedTranche: SDSEarlyReleaseTranche,
    commencementDate: LocalDate?,
    dates: MutableMap<ReleaseDateType, LocalDate>,
    breakdownByReleaseDateType: MutableMap<ReleaseDateType, ReleaseDateCalculationBreakdown>,
  ) {
    val early = earlyReleaseResult.dates[dateType]
    val standard = standardReleaseResult.dates[dateType]
    if (commencementDate != null && early != null && early < commencementDate && standard != null && standard >= commencementDate) {
      dates[dateType] = commencementDate
      breakdownByReleaseDateType[dateType] = ReleaseDateCalculationBreakdown(
        setOf(if (allocatedTranche == SDSEarlyReleaseTranche.TRANCHE_1) CalculationRule.SDS_EARLY_RELEASE_ADJUSTED_TO_TRANCHE_1_COMMENCEMENT else CalculationRule.SDS_EARLY_RELEASE_ADJUSTED_TO_TRANCHE_2_COMMENCEMENT),
        releaseDate = commencementDate,
        unadjustedDate = early,
      )
    } else if (standard != null && (commencementDate == null || standard.isBefore(commencementDate) || standard == early)) {
      dates[dateType] = standard
      standardReleaseResult.breakdownByReleaseDateType[dateType]?.let {
        breakdownByReleaseDateType[dateType] = it.copy(
          rules = it.rules + CalculationRule.SDS_STANDARD_RELEASE_APPLIES,
        )
      }
      // Handle TUSED adjustment to standard when normal CRD is being used.
      if (dateType == ReleaseDateType.CRD && standardReleaseResult.dates.containsKey(ReleaseDateType.TUSED)) {
        standardReleaseResult.dates[ReleaseDateType.TUSED]?.let {
          dates[ReleaseDateType.TUSED] = it
        }
        standardReleaseResult.breakdownByReleaseDateType[ReleaseDateType.TUSED]?.let {
          breakdownByReleaseDateType[ReleaseDateType.TUSED] = it
        }
      }
    } else {
      breakdownByReleaseDateType[dateType]?.let {
        breakdownByReleaseDateType[dateType] = it.copy(
          rules = it.rules + CalculationRule.SDS_EARLY_RELEASE_APPLIES,
        )
      }
    }
  }

  private fun applyReleaseRules(
    dateType: ReleaseDateType,
    earlyReleaseResult: CalculationResult,
    standardReleaseResult: CalculationResult,
    commencementDate: LocalDate?,
    breakdownByReleaseDateType: MutableMap<ReleaseDateType, ReleaseDateCalculationBreakdown>,
  ) {
    val early = earlyReleaseResult.dates[dateType]
    val standard = standardReleaseResult.dates[dateType]
    if (standard != null && (commencementDate == null || standard.isBefore(commencementDate) || standard == early)) {
      standardReleaseResult.breakdownByReleaseDateType[dateType]?.let {
        breakdownByReleaseDateType[dateType] = it.copy(
          rules = it.rules + CalculationRule.SDS_STANDARD_RELEASE_APPLIES,
        )
      }
    } else {
      breakdownByReleaseDateType[dateType]?.let {
        breakdownByReleaseDateType[dateType] = it.copy(
          rules = it.rules + CalculationRule.SDS_EARLY_RELEASE_APPLIES,
        )
      }
    }
  }

  companion object {
    private val DATE_TYPES_TO_ADJUST_TO_COMMENCEMENT_DATE = listOf(
      ReleaseDateType.CRD,
      ReleaseDateType.ERSED,
      ReleaseDateType.HDCED,
      ReleaseDateType.PED,
    )
  }
}
