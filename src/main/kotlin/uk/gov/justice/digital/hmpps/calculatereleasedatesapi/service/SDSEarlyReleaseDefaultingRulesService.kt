package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.stereotype.Service
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
) {

  fun requiresRecalculation(booking: Booking, result: CalculationResult, trancheCommencementDate: LocalDate?): Boolean {
    return hasAnySDSEarlyRelease(booking)
  }

  fun mergeResults(
    earlyReleaseResult: CalculationResult,
    standardReleaseResult: CalculationResult,
    trancheCommencementDate: LocalDate?,
    allocatedTranche: SDSEarlyReleaseTranche,
    originalBooking: Booking,
    trancheOneCommencementDate: LocalDate,
  ): CalculationResult {
    val dates = earlyReleaseResult.dates.toMutableMap()
    val breakdownByReleaseDateType = earlyReleaseResult.breakdownByReleaseDateType.toMutableMap()
    var overriddenTranche = allocatedTranche

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
    }

    handleTUSEDForSDSRecallsBeforeTrancheOneCommencement(
      dates,
      originalBooking,
      trancheOneCommencementDate,
      standardReleaseResult,
      breakdownByReleaseDateType,
    )

    handleCRDorARDandPRRD(dates)

    overriddenTranche = if (dates == standardReleaseResult.dates) {
      SDSEarlyReleaseTranche.TRANCHE_0
    } else {
      allocatedTranche
    }

    return CalculationResult(
      dates,
      breakdownByReleaseDateType,
      earlyReleaseResult.otherDates,
      earlyReleaseResult.effectiveSentenceLength,
      sdsEarlyReleaseAllocatedTranche = overriddenTranche,
      sdsEarlyReleaseTranche = overriddenTranche,
      affectedBySds40 = (dates != standardReleaseResult.dates),
    )
  }

  private fun handleCRDorARDandPRRD(
    dates: MutableMap<ReleaseDateType, LocalDate>,
  ) {
    if ((dates.containsKey(ReleaseDateType.CRD).or(dates.containsKey(ReleaseDateType.ARD)))
        .and(dates.containsKey(ReleaseDateType.PRRD))
    ) {
      val controllingDate =
        if (dates[ReleaseDateType.ARD] != null) ReleaseDateType.ARD to dates[ReleaseDateType.ARD] else ReleaseDateType.CRD to dates[ReleaseDateType.CRD]

      if (controllingDate.second?.isAfter(dates[ReleaseDateType.PRRD]) == true) {
        dates.remove(ReleaseDateType.PRRD)
      } else {
        dates.remove(controllingDate.first)

        // PRRD is later than any other release date, therefore can not have a HDCED
        // See BookingExtractionService#extractMultiple
        dates.remove(ReleaseDateType.HDCED4PLUS)
        dates.remove(ReleaseDateType.HDCED)
      }
    }
  }

  fun handleTUSEDForSDSRecallsBeforeTrancheOneCommencement(
    dates: MutableMap<ReleaseDateType, LocalDate>,
    originalBooking: Booking,
    trancheOneCommencementDate: LocalDate,
    standardReleaseResult: CalculationResult,
    breakdownByReleaseDateType: MutableMap<ReleaseDateType, ReleaseDateCalculationBreakdown>,
  ) {
    val latestReleaseDate = extractionService.mostRecentSentence(
      originalBooking.getAllExtractableSentences(),
      SentenceCalculation::adjustedDeterminateReleaseDate,
    ).sentenceCalculation.adjustedDeterminateReleaseDate

    if (dates.containsKey(ReleaseDateType.TUSED) && !latestReleaseDate.isAfterOrEqualTo(trancheOneCommencementDate)) {
      if (originalBooking.getAllExtractableSentences().any {
          it.releaseDateTypes.contains(ReleaseDateType.TUSED) &&
            (
              it is StandardDeterminateSentence ||
                (
                  it is ConsecutiveSentence &&
                    it.orderedSentences.any { sentence -> sentence is StandardDeterminateSentence }
                  )
              ) &&
            it.recallType != null
        }
      ) {
        standardReleaseResult.dates[ReleaseDateType.TUSED]?.let {
          dates[ReleaseDateType.TUSED] = it
        }
        standardReleaseResult.breakdownByReleaseDateType[ReleaseDateType.TUSED]?.let {
          breakdownByReleaseDateType[ReleaseDateType.TUSED] = it
        }
      }
    }
  }

  private fun hasAnySDSEarlyRelease(anInitialCalc: Booking) =
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
    } else if (standard != null && ((commencementDate != null && standard.isBefore(commencementDate)) || commencementDate == null)) {
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

  companion object {
    private val DATE_TYPES_TO_ADJUST_TO_COMMENCEMENT_DATE = listOf(
      ReleaseDateType.CRD,
      ReleaseDateType.ERSED,
      ReleaseDateType.HDCED,
      ReleaseDateType.HDCED4PLUS,
      ReleaseDateType.PED,
    )
  }
}
