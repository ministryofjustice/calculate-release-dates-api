package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.handlers

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.EarlyReleaseConfiguration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.EarlyReleaseConfigurations
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.EarlyReleaseTrancheType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.RecallCalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceIdentificationTrack
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.NoValidReturnToCustodyDateException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculableSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offender
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RecallType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.StandardDeterminateSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineCalculator
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineHandleResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineTrackingData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.isAfterOrEqualTo
import java.rmi.UnexpectedException
import java.time.LocalDate

abstract class TimelineCalculationHandler(
  protected val timelineCalculator: TimelineCalculator,
  protected val earlyReleaseConfigurations: EarlyReleaseConfigurations,
) {
  abstract fun handle(
    timelineCalculationDate: LocalDate,
    timelineTrackingData: TimelineTrackingData,
  ): TimelineHandleResult

  fun multiplierFnForDate(
    timelineCalculationDate: LocalDate,
    allocatedTrancheDate: LocalDate?,
    offender: Offender,
  ): (sentence: CalculableSentence) -> Double = { sentence ->
    multiplerForSentence(
      timelineCalculationDate,
      allocatedTrancheDate,
      sentence,
      offender,
    )
  }

  fun findRecallCalculation(
    timelineCalculationDate: LocalDate,
    allocatedEarlyReleaseConfiguration: EarlyReleaseConfiguration?,
  ): (CalculableSentence, LocalDate?, Pair<Int, LocalDate>) -> Pair<Int, LocalDate> = { sentence, returnToCustodyDate, standardCalculation ->
    when (val recallType = sentence.recallType) {
      RecallType.STANDARD_RECALL -> standardCalculation

      RecallType.FIXED_TERM_RECALL_14,
      RecallType.FIXED_TERM_RECALL_28,
      -> calculateFixedTermRecall(returnToCustodyDate, recallType)

      RecallType.FIXED_TERM_RECALL_56 -> {
        if (returnToCustodyDate == null) {
          throw NoValidReturnToCustodyDateException("No return to custody date available")
        }

        val ftr56Configuration = earlyReleaseConfigurations.configurations.find { it.recallCalculation == RecallCalculationType.FTR_56 }
        if (ftr56Configuration != null && returnToCustodyDate.isAfterOrEqualTo(ftr56Configuration.earliestTranche())) {
          calculateFixedTermRecall(returnToCustodyDate, recallType)
        } else if (allocatedEarlyReleaseConfiguration != null && allocatedEarlyReleaseConfiguration == ftr56Configuration) {
          calculateFixedTermRecall(returnToCustodyDate, recallType)
        } else {
          standardCalculation
        }
      }

      RecallType.STANDARD_RECALL_255 ->
        error("STANDARD_RECALL_255 is not supported yet")
      null ->
        error("Recall type is missing, with a recall, on sentence: $sentence")
    }
  }

  private fun calculateFixedTermRecall(returnToCustodyDate: LocalDate?, recallType: RecallType): Pair<Int, LocalDate> {
    if (returnToCustodyDate == null) {
      throw NoValidReturnToCustodyDateException("No return to custody date available")
    }
    val days = recallType.lengthInDays!!
    return days to returnToCustodyDate
      .plusDays(days.toLong())
      .minusDays(1)
  }

  /**
   Historic release point is before SDS40 tranching started
   */
  fun historicMultiplierFnForDate(
    offender: Offender,
  ): (sentence: CalculableSentence) -> Double = { sentence ->
    multiplerForSentence(
      earlyReleaseConfigurations.configurations.minOf { it.earliestTranche() }.minusDays(1),
      null,
      sentence,
      offender,
    )
  }

  private fun multiplerForSentence(
    timelineCalculationDate: LocalDate,
    allocatedTrancheDate: LocalDate?,
    sentence: CalculableSentence,
    offender: Offender,
  ): Double = if (sentence.identificationTrack.isMultiplierFixed()) {
    sentence.identificationTrack.fixedMultiplier()
  } else {
    sdsReleaseMultiplier(sentence, timelineCalculationDate, allocatedTrancheDate, offender)
  }

  private fun sdsReleaseMultiplier(
    sentence: CalculableSentence,
    timelineCalculationDate: LocalDate,
    allocatedTrancheDate: LocalDate?,
    offender: Offender,
  ): Double {
    if (sentence is StandardDeterminateSentence) {
      val latestEarlyReleaseConfig =
        earlyReleaseConfigurations.configurations
          .filter { timelineCalculationDate.isAfterOrEqualTo(it.earliestTranche()) }
          .filter { it.releaseMultiplier != null }
          .maxByOrNull { it.earliestTranche() }
      if (latestEarlyReleaseConfig != null) {
        // They are tranched.
        if (allocatedTrancheDate != null) {
          if (latestEarlyReleaseConfig.matchesFilter(sentence, offender)) {
            return getMultiplerForConfiguration(latestEarlyReleaseConfig, timelineCalculationDate, sentence)
          }
        } else if (sentence.sentencedAt.isAfterOrEqualTo(latestEarlyReleaseConfig.earliestTranche())) {
          if (latestEarlyReleaseConfig.matchesFilter(sentence, offender)) {
            return getMultiplerForConfiguration(latestEarlyReleaseConfig, timelineCalculationDate, sentence)
          }
        }
      }
    }
    return defaultSDSReleaseMultiplier(sentence)
  }

  private fun defaultSDSReleaseMultiplier(sentence: CalculableSentence): Double = when (sentence.identificationTrack) {
    SentenceIdentificationTrack.SDS -> 0.5
    SentenceIdentificationTrack.SDS_PLUS -> 2.toDouble().div(3)
    else -> throw UnexpectedException("Unknown default release multipler.")
  }

  private fun getMultiplerForConfiguration(
    earlyReleaseConfig: EarlyReleaseConfiguration,
    timelineCalculationDate: LocalDate,
    sentence: StandardDeterminateSentence,
  ): Double {
    val sds40Tranche3 = earlyReleaseConfig.tranches.find { it.type == EarlyReleaseTrancheType.SDS_40_TRANCHE_3 }
    if (sds40Tranche3 != null && timelineCalculationDate.isAfterOrEqualTo(sds40Tranche3.date) && sentence.hasAnSDSEarlyReleaseExclusion.trancheThreeExclusion) {
      return defaultSDSReleaseMultiplier(sentence)
    }
    return earlyReleaseConfig.releaseMultiplier!![sentence.identificationTrack]!!.toDouble()
  }
}
