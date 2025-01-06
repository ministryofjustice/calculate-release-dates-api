package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.SDS40TrancheConfiguration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationRule
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SDSEarlyReleaseTranche
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculableSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ConsecutiveSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ReleaseDateCalculationBreakdown
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.StandardDeterminateSentence
import java.time.LocalDate

@Service
class SDSEarlyReleaseDefaultingRulesService(
  val trancheConfiguration: SDS40TrancheConfiguration,
) {
  fun applySDSEarlyReleaseRulesAndFinalizeDates(
    earlyReleaseResult: CalculationResult,
    standardReleaseResult: CalculationResult,
    trancheCommencementDate: LocalDate?,
    tranche: SDSEarlyReleaseTranche,
    sentences: List<CalculableSentence>,
  ): CalculationResult {
    val dates = earlyReleaseResult.dates.toMutableMap()
    val breakdownByReleaseDateType = earlyReleaseResult.breakdownByReleaseDateType.toMutableMap()

    adjustDates(
      earlyReleaseResult,
      standardReleaseResult,
      trancheCommencementDate,
      tranche,
      dates,
      breakdownByReleaseDateType,
      sentences,
    )

    return createFinalCalculationResult(
      dates,
      breakdownByReleaseDateType,
      earlyReleaseResult,
      standardReleaseResult,
      tranche,
    )
  }

  private fun adjustDates(
    earlyReleaseResult: CalculationResult,
    standardReleaseResult: CalculationResult,
    trancheCommencementDate: LocalDate?,
    allocatedTranche: SDSEarlyReleaseTranche,
    dates: MutableMap<ReleaseDateType, LocalDate>,
    breakdownByReleaseDateType: MutableMap<ReleaseDateType, ReleaseDateCalculationBreakdown>,
    sentences: List<CalculableSentence>,
  ) {
    adjustDatesForEarlyOrStandardRelease(
      earlyReleaseResult,
      standardReleaseResult,
      trancheCommencementDate,
      allocatedTranche,
      dates,
      breakdownByReleaseDateType,
    )

    adjustTusedForPreTrancheOneRecalls(
      dates,
      standardReleaseResult,
      breakdownByReleaseDateType,
      sentences,
    )

    adjustHdcedAndPrrdBasedOnCrdOrArd(
      dates,
      earlyReleaseResult.otherDates.toMutableMap(),
      breakdownByReleaseDateType,
      standardReleaseResult,
    )

    removeHdcedAndErsedIfMatchingRelease(dates)
  }

  private fun createFinalCalculationResult(
    dates: Map<ReleaseDateType, LocalDate>,
    breakdownByReleaseDateType: Map<ReleaseDateType, ReleaseDateCalculationBreakdown>,
    earlyReleaseResult: CalculationResult,
    standardReleaseResult: CalculationResult,
    allocatedTranche: SDSEarlyReleaseTranche,
  ): CalculationResult = CalculationResult(
    dates = dates,
    breakdownByReleaseDateType = breakdownByReleaseDateType,
    otherDates = earlyReleaseResult.otherDates,
    effectiveSentenceLength = earlyReleaseResult.effectiveSentenceLength,
    sdsEarlyReleaseAllocatedTranche = allocatedTranche,
    sdsEarlyReleaseTranche = allocatedTranche,
    affectedBySds40 = (dates != standardReleaseResult.dates),
  )

  private fun getArdOrCrd(
    dates: MutableMap<ReleaseDateType, LocalDate>,
  ): Pair<ReleaseDateType, LocalDate?> =
    when {
      dates.containsKey(ReleaseDateType.ARD) -> ReleaseDateType.ARD to dates.getValue(ReleaseDateType.ARD)
      dates.containsKey(ReleaseDateType.CRD) -> ReleaseDateType.CRD to dates.getValue(ReleaseDateType.CRD)
      else -> throw IllegalStateException("Neither ARD or CRD found in dates")
    }

  private fun adjustHdcedAndPrrdBasedOnCrdOrArd(
    dates: MutableMap<ReleaseDateType, LocalDate>,
    otherDates: MutableMap<ReleaseDateType, LocalDate>,
    breakdownByReleaseDateType: MutableMap<ReleaseDateType, ReleaseDateCalculationBreakdown>,
    standardReleaseResult: CalculationResult,
  ) {
    if (dates.containsKey(ReleaseDateType.CRD) || dates.containsKey(ReleaseDateType.ARD)) {
      val (ardOrCrdType, ardOrCrdDate) = getArdOrCrd(dates)

      adjustHdcedForPrrd(dates, otherDates, breakdownByReleaseDateType, standardReleaseResult)
      adjustPrrdForArdOrCrd(dates, ardOrCrdType, ardOrCrdDate)
    }
  }

  private fun adjustHdcedForPrrd(
    dates: MutableMap<ReleaseDateType, LocalDate>,
    otherDates: MutableMap<ReleaseDateType, LocalDate>,
    breakdownByReleaseDateType: MutableMap<ReleaseDateType, ReleaseDateCalculationBreakdown>,
    standardReleaseResult: CalculationResult,
  ) {
    val hdcedDate = dates[ReleaseDateType.HDCED] ?: return
    val prrdDate = dates[ReleaseDateType.PRRD] ?: otherDates[ReleaseDateType.PRRD] ?: return

    if (prrdDate.isAfter(hdcedDate)) {
      updateHdcedDateAndBreakdown(dates, prrdDate, breakdownByReleaseDateType, standardReleaseResult)
    }
  }

  private fun updateHdcedDateAndBreakdown(
    dates: MutableMap<ReleaseDateType, LocalDate>,
    newDate: LocalDate,
    breakdownByReleaseDateType: MutableMap<ReleaseDateType, ReleaseDateCalculationBreakdown>,
    standardReleaseResult: CalculationResult,
  ) {
    dates[ReleaseDateType.HDCED] = newDate
    standardReleaseResult.breakdownByReleaseDateType[ReleaseDateType.HDCED]?.let { standardBreakdown ->
      breakdownByReleaseDateType[ReleaseDateType.HDCED] = standardBreakdown.copy(
        rules = standardBreakdown.rules + CalculationRule.HDCED_ADJUSTED_TO_CONCURRENT_PRRD,
      )
    }
  }

  private fun adjustPrrdForArdOrCrd(
    dates: MutableMap<ReleaseDateType, LocalDate>,
    ardOrCrdType: ReleaseDateType,
    ardOrCrdDate: LocalDate?,
  ) {
    if (dates.containsKey(ReleaseDateType.PRRD)) {
      if (ardOrCrdDate?.isAfter(dates[ReleaseDateType.PRRD]) == true) {
        dates.remove(ReleaseDateType.PRRD)
      } else {
        // PRRD is later than ARD or CRD, therefore can not have a HDCED
        dates.remove(ardOrCrdType)
        dates.remove(ReleaseDateType.HDCED)
      }
    }
  }

  // Removes HDCED and ERSED dates if they match CRD/ARD and if they match with a tranche commencement date
  fun removeHdcedAndErsedIfMatchingRelease(dates: MutableMap<ReleaseDateType, LocalDate>) {
    val crdOrdArd = dates[ReleaseDateType.CRD] ?: dates[ReleaseDateType.ARD] ?: return

    val hdcedAndErsedDateTypes = listOf(ReleaseDateType.HDCED, ReleaseDateType.ERSED)
    val trancheCommencementDates = listOf(
      trancheConfiguration.trancheOneCommencementDate,
      trancheConfiguration.trancheTwoCommencementDate,
    )

    hdcedAndErsedDateTypes.forEach { dateType ->
      dates[dateType]?.let { hdcedOrErsedDate ->
        if (hdcedOrErsedDate == crdOrdArd && hdcedOrErsedDate in trancheCommencementDates) {
          dates.remove(dateType)
        }
      }
    }
  }

  fun adjustTusedForPreTrancheOneRecalls(
    dates: MutableMap<ReleaseDateType, LocalDate>,
    standardReleaseResult: CalculationResult,
    breakdownByReleaseDateType: MutableMap<ReleaseDateType, ReleaseDateCalculationBreakdown>,
    sentences: List<CalculableSentence>,
  ) {
    val originalCrd = standardReleaseResult.dates[ReleaseDateType.CRD]

    if (originalCrd != null && shouldAdjustTused(dates, originalCrd) && hasEligibleRecallTusedSentence(sentences)) {
      applyStandardTused(dates, standardReleaseResult, breakdownByReleaseDateType)
    }
  }

  private fun shouldAdjustTused(dates: Map<ReleaseDateType, LocalDate>, latestReleaseDate: LocalDate): Boolean =
    dates.containsKey(ReleaseDateType.TUSED) && latestReleaseDate.isBefore(trancheConfiguration.trancheOneCommencementDate)

  private fun applyStandardTused(
    dates: MutableMap<ReleaseDateType, LocalDate>,
    standardReleaseResult: CalculationResult,
    breakdownByReleaseDateType: MutableMap<ReleaseDateType, ReleaseDateCalculationBreakdown>,
  ) {
    standardReleaseResult.dates[ReleaseDateType.TUSED]?.let {
      dates[ReleaseDateType.TUSED] = it
    }
    standardReleaseResult.breakdownByReleaseDateType[ReleaseDateType.TUSED]?.let {
      breakdownByReleaseDateType[ReleaseDateType.TUSED] = it
    }
  }

  private fun hasEligibleRecallTusedSentence(sentences: List<CalculableSentence>): Boolean {
    return sentences.any { sentence ->
      sentence.releaseDateTypes.contains(ReleaseDateType.TUSED) &&
        sentence.recallType != null &&
        when (sentence) {
          is StandardDeterminateSentence -> true
          is ConsecutiveSentence -> hasStandardDeterminateSentence(sentence)
          else -> false
        }
    }
  }

  private fun hasStandardDeterminateSentence(sentence: ConsecutiveSentence): Boolean =
    sentence.orderedSentences.any { it is StandardDeterminateSentence }

  fun hasAnyReleaseBeforeTrancheCommencement(
    early: CalculationResult,
    late: CalculationResult,
    trancheCommencementDate: LocalDate?,
  ): Boolean {
    if (trancheCommencementDate == null) {
      return false
    }

    return DATE_TYPES_TO_ADJUST_TO_COMMENCEMENT_DATE.mapNotNull { dateType ->
      early.dates[dateType] ?: late.dates[dateType]
    }.any { it < trancheCommencementDate }
  }

  private fun adjustDatesForEarlyOrStandardRelease(
    earlyReleaseResult: CalculationResult,
    standardReleaseResult: CalculationResult,
    trancheCommencementDate: LocalDate?,
    allocatedTranche: SDSEarlyReleaseTranche,
    dates: MutableMap<ReleaseDateType, LocalDate>,
    breakdownByReleaseDateType: MutableMap<ReleaseDateType, ReleaseDateCalculationBreakdown>,
  ) {
    if (hasAnyReleaseBeforeTrancheCommencement(earlyReleaseResult, standardReleaseResult, trancheCommencementDate)) {
      adjustDatesForEarlyRelease(
        earlyReleaseResult,
        standardReleaseResult,
        allocatedTranche,
        trancheCommencementDate,
        dates,
        breakdownByReleaseDateType,
      )
    } else if (allocatedTranche != SDSEarlyReleaseTranche.TRANCHE_0) {
      adjustDatesForNonEarlyRelease(
        earlyReleaseResult,
        standardReleaseResult,
        trancheCommencementDate,
        breakdownByReleaseDateType,
      )
    }
  }

  private fun adjustDatesForEarlyRelease(
    earlyReleaseResult: CalculationResult,
    standardReleaseResult: CalculationResult,
    allocatedTranche: SDSEarlyReleaseTranche,
    trancheCommencementDate: LocalDate?,
    dates: MutableMap<ReleaseDateType, LocalDate>,
    breakdownByReleaseDateType: MutableMap<ReleaseDateType, ReleaseDateCalculationBreakdown>,
  ) {
    DATE_TYPES_TO_ADJUST_TO_COMMENCEMENT_DATE.forEach { releaseDateType ->
      determineAndApplyReleaseDate(
        ReleaseDateContext(
          releaseDateType,
          earlyReleaseResult,
          standardReleaseResult,
          allocatedTranche,
          trancheCommencementDate,
          dates,
          breakdownByReleaseDateType,
        ),
      )
    }
  }

  private fun adjustDatesForNonEarlyRelease(
    earlyReleaseResult: CalculationResult,
    standardReleaseResult: CalculationResult,
    trancheCommencementDate: LocalDate?,
    breakdownByReleaseDateType: MutableMap<ReleaseDateType, ReleaseDateCalculationBreakdown>,
  ) {
    DATE_TYPES_TO_ADJUST_TO_COMMENCEMENT_DATE.forEach { releaseDateType ->
      determineAndApplyReleaseRule(
        releaseDateType,
        earlyReleaseResult,
        standardReleaseResult,
        trancheCommencementDate,
        breakdownByReleaseDateType,
      )
    }
  }

  private fun determineAndApplyReleaseDate(context: ReleaseDateContext) {
    val early = context.earlyReleaseResult.dates[context.dateType]
    val standard = context.standardReleaseResult.dates[context.dateType]

    when {
      shouldUseTrancheCommencementDate(early, standard, context.trancheCommencementDate) ->
        applyTrancheCommencementDate(context, early!!)

      shouldUseStandardDate(standard, early, context.trancheCommencementDate) ->
        applyStandardDate(context, standard!!)

      else ->
        applyEarlyReleaseRule(context)
    }
  }

  private fun shouldUseTrancheCommencementDate(
    early: LocalDate?,
    standard: LocalDate?,
    trancheCommencementDate: LocalDate?,
  ): Boolean =
    trancheCommencementDate != null && early != null && early < trancheCommencementDate && standard != null && standard >= trancheCommencementDate

  private fun shouldUseStandardDate(
    standard: LocalDate?,
    early: LocalDate?,
    trancheCommencementDate: LocalDate?,
  ): Boolean =
    standard != null && (trancheCommencementDate == null || standard.isBefore(trancheCommencementDate) || standard == early)

  private fun applyTrancheCommencementDate(context: ReleaseDateContext, early: LocalDate) {
    context.dates[context.dateType] = context.trancheCommencementDate!!
    context.breakdownByReleaseDateType[context.dateType] = ReleaseDateCalculationBreakdown(
      setOf(
        if (context.allocatedTranche == SDSEarlyReleaseTranche.TRANCHE_1) {
          CalculationRule.SDS_EARLY_RELEASE_ADJUSTED_TO_TRANCHE_1_COMMENCEMENT
        } else {
          CalculationRule.SDS_EARLY_RELEASE_ADJUSTED_TO_TRANCHE_2_COMMENCEMENT
        },
      ),
      releaseDate = context.trancheCommencementDate,
      unadjustedDate = early,
    )
  }

  private fun applyStandardDate(context: ReleaseDateContext, standard: LocalDate) {
    context.dates[context.dateType] = standard
    context.standardReleaseResult.breakdownByReleaseDateType[context.dateType]?.let {
      context.breakdownByReleaseDateType[context.dateType] = it.copy(
        rules = it.rules + CalculationRule.SDS_STANDARD_RELEASE_APPLIES,
      )
    }
    // Handle TUSED adjustment to standard when normal CRD is being used.
    useStandardTusedIfCrdExists(context)
  }

  private fun applyEarlyReleaseRule(context: ReleaseDateContext) {
    context.breakdownByReleaseDateType[context.dateType]?.let {
      context.breakdownByReleaseDateType[context.dateType] = it.copy(
        rules = it.rules + CalculationRule.SDS_EARLY_RELEASE_APPLIES,
      )
    }
  }

  private fun useStandardTusedIfCrdExists(context: ReleaseDateContext) {
    if (context.dateType != ReleaseDateType.CRD) return

    context.standardReleaseResult.dates[ReleaseDateType.TUSED]?.let { standardTusedDate ->
      context.dates[ReleaseDateType.TUSED] = standardTusedDate
      context.standardReleaseResult.breakdownByReleaseDateType[ReleaseDateType.TUSED]?.let { standardTusedBreakdown ->
        context.breakdownByReleaseDateType[ReleaseDateType.TUSED] = standardTusedBreakdown
      }
    }
  }

  private fun determineAndApplyReleaseRule(
    dateType: ReleaseDateType,
    earlyReleaseResult: CalculationResult,
    standardReleaseResult: CalculationResult,
    trancheCommencementDate: LocalDate?,
    breakdownByReleaseDateType: MutableMap<ReleaseDateType, ReleaseDateCalculationBreakdown>,
  ) {
    val early = earlyReleaseResult.dates[dateType]
    val standard = standardReleaseResult.dates[dateType]

    val ruleToApply = when {
      shouldApplyStandardRelease(
        standard,
        early,
        trancheCommencementDate,
      ) -> CalculationRule.SDS_STANDARD_RELEASE_APPLIES

      else -> CalculationRule.SDS_EARLY_RELEASE_APPLIES
    }

    log.info(">> dateType: $dateType rule to apply: $ruleToApply earlyReleaseResult = $early standardReleaseResult = $standard")

    updateBreakdown(dateType, ruleToApply, breakdownByReleaseDateType, standardReleaseResult)
  }

  private fun shouldApplyStandardRelease(
    standard: LocalDate?,
    early: LocalDate?,
    trancheCommencementDate: LocalDate?,
  ): Boolean =
    standard != null && (trancheCommencementDate == null || standard.isBefore(trancheCommencementDate) || standard == early)

  private fun updateBreakdown(
    dateType: ReleaseDateType,
    rule: CalculationRule,
    breakdownByReleaseDateType: MutableMap<ReleaseDateType, ReleaseDateCalculationBreakdown>,
    standardReleaseResult: CalculationResult,
  ) {
    val existingBreakdown = if (rule == CalculationRule.SDS_STANDARD_RELEASE_APPLIES) {
      standardReleaseResult.breakdownByReleaseDateType[dateType]
    } else {
      breakdownByReleaseDateType[dateType]
    }

    existingBreakdown?.let {
      breakdownByReleaseDateType[dateType] = it.copy(rules = it.rules + rule)
    }
  }

  companion object {
    private val DATE_TYPES_TO_ADJUST_TO_COMMENCEMENT_DATE = listOf(
      ReleaseDateType.CRD,
      ReleaseDateType.ERSED,
      ReleaseDateType.HDCED,
      ReleaseDateType.PED,
    )

    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  private data class ReleaseDateContext(
    val dateType: ReleaseDateType,
    val earlyReleaseResult: CalculationResult,
    val standardReleaseResult: CalculationResult,
    val allocatedTranche: SDSEarlyReleaseTranche,
    val trancheCommencementDate: LocalDate?,
    val dates: MutableMap<ReleaseDateType, LocalDate>,
    val breakdownByReleaseDateType: MutableMap<ReleaseDateType, ReleaseDateCalculationBreakdown>,
  )
}
