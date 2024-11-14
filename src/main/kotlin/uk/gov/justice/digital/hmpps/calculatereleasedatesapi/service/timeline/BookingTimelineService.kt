package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.SDS40TrancheConfiguration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType.ADDITIONAL_DAYS_AWARDED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType.RECALL_REMAND
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType.RECALL_TAGGED_BAIL
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType.REMAND
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType.RESTORATION_OF_ADDITIONAL_DAYS_AWARDED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType.TAGGED_BAIL
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType.UNLAWFULLY_AT_LARGE
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Adjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculableSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationOptions
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationOutput
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CustodialPeriod
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offender
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.SDSEarlyReleaseDefaultingRulesService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.WorkingDayService
import java.time.LocalDate

@Service
class BookingTimelineService(
  private val workingDayService: WorkingDayService,
  private val trancheConfiguration: SDS40TrancheConfiguration,
  private val sdsEarlyReleaseDefaultingRulesService: SDSEarlyReleaseDefaultingRulesService,
  private val timelineCalculator: TimelineCalculator,
  private val timelineAwardedAdjustmentCalculationHandler: TimelineAwardedAdjustmentCalculationHandler,
  private val timelineTrancheCalculationHandler: TimelineTrancheCalculationHandler,
  private val timelineSentenceCalculationHandler: TimelineSentenceCalculationHandler,
  private val timelineUalAdjustmentCalculationHandler: TimelineUalAdjustmentCalculationHandler,
) {

  fun calculate(sentences: List<CalculableSentence>, adjustments: Adjustments, offender: Offender, returnToCustodyDate: LocalDate?, options: CalculationOptions): CalculationOutput {
    val futureData = TimelineFutureData(
      taggedBail = adjustments.getOrEmptyList(TAGGED_BAIL),
      remand = adjustments.getOrEmptyList(REMAND),
      recallTaggedBail = adjustments.getOrEmptyList(RECALL_TAGGED_BAIL),
      recallRemand = adjustments.getOrEmptyList(RECALL_REMAND),
      additional = adjustments.getOrEmptyList(ADDITIONAL_DAYS_AWARDED),
      restored = adjustments.getOrEmptyList(RESTORATION_OF_ADDITIONAL_DAYS_AWARDED),
      ual = adjustments.getOrEmptyList(UNLAWFULLY_AT_LARGE),
      sentences = sentences.sortedBy { it.sentencedAt },
    )

    val calculationsByDate = getCalculationsByDate(sentences, futureData)

    val earliestSentence = futureData.sentences.minBy { it.sentencedAt }
    val timelineTrackingData = TimelineTrackingData(
      futureData,
      calculationsByDate,
      latestRelease = earliestSentence.sentencedAt to earliestSentence,
      returnToCustodyDate,
      offender,
      options,
    )

    calculationsByDate.forEach { (date, calculations) ->
      checkForReleasesAndLicenseExpiry(date, timelineTrackingData)

      calculations.sortedBy { it.type.ordinal }.forEachIndexed { _, it ->
        val result = handlerFor(it.type).handle(date, timelineTrackingData)
        if (result.skipCalculation) {
          return@forEach
        }
      }

      calculateLatestCustodialRelease(timelineTrackingData)
    }

    return calculateFinalReleaseDatesAfterTimeline(timelineTrackingData)
  }

  private fun calculateFinalReleaseDatesAfterTimeline(timelineTrackingData: TimelineTrackingData): CalculationOutput {
    with(timelineTrackingData) {
      if (custodialSentences.isNotEmpty()) {
        releasedSentences.add(
          CustodialPeriod(
            custodialSentences.minOf { it.sentencedAt },
            latestRelease.first,
            custodialSentences.toList(),
          ),
        )
        custodialSentences.clear()
      }
      latestCalculation =
        timelineCalculator.getLatestCalculation(releasedSentences.map { it.sentences }, offender).copy(
          sdsEarlyReleaseAllocatedTranche = trancheAndCommencement.first,
          sdsEarlyReleaseTranche = trancheAndCommencement.first,
        )

      if (beforeTrancheCalculation != null) {
        latestCalculation = sdsEarlyReleaseDefaultingRulesService.applySDSEarlyReleaseRulesAndFinalizeDates(
          latestCalculation,
          beforeTrancheCalculation!!,
          trancheAndCommencement.second!!,
          trancheAndCommencement.first,
          releasedSentences.flatMap { it.sentences },
        )
      }

      return CalculationOutput(
        releasedSentences.flatMap { it.sentences },
        releasedSentences,
        latestCalculation,
      )
    }
  }

  private fun handlerFor(type: TimelineCalculationType): TimelineCalculationHandler {
    return when (type) {
      TimelineCalculationType.SENTENCED -> timelineSentenceCalculationHandler
      TimelineCalculationType.ADDITIONAL_DAYS, TimelineCalculationType.RESTORATION_DAYS -> timelineAwardedAdjustmentCalculationHandler
      TimelineCalculationType.UAL -> timelineUalAdjustmentCalculationHandler
      TimelineCalculationType.TRANCHE_1, TimelineCalculationType.TRANCHE_2 -> timelineTrancheCalculationHandler
    }
  }

  private fun calculateLatestCustodialRelease(timelineTrackingData: TimelineTrackingData) {
    with(timelineTrackingData) {
      if (custodialSentences.isNotEmpty()) {
        val latestReleaseSentence = custodialSentences.maxBy { it.sentenceCalculation.adjustedDeterminateReleaseDate }
        val releaseDate =
          if (beforeTrancheCalculation != null && latestReleaseSentence.sentenceCalculation.adjustedDeterminateReleaseDate.isBefore(
              trancheAndCommencement.second!!,
            )
          ) {
            trancheAndCommencement.second!!
          } else {
            latestReleaseSentence.sentenceCalculation.adjustedDeterminateReleaseDate
          }
        latestRelease = workingDayService.previousWorkingDay(releaseDate).date to latestReleaseSentence
      }
    }
  }

  private fun checkForReleasesAndLicenseExpiry(date: LocalDate, timelineTrackingData: TimelineTrackingData) {
    with(timelineTrackingData) {
      if (date.isAfter(latestRelease.first)) {
        if (custodialSentences.isNotEmpty()) {
          // Release has happened. do extraction here.
          releasedSentences.add(
            CustodialPeriod(
              custodialSentences.minOf { it.sentencedAt },
              latestRelease.first,
              custodialSentences.toList(),
            ),
          )
          custodialSentences.forEach {
            if (it.sentenceCalculation.licenceExpiryDate?.isAfter(date) == true) {
              licenseSentences.add(it)
            }
          }
          custodialSentences.clear()
        }
        latestCalculation = timelineCalculator.getLatestCalculation(releasedSentences.map { it.sentences }, offender)
      }
      if (licenseSentences.isNotEmpty()) {
        licenseSentences.removeIf {
          date.isAfter(it.sentenceCalculation.licenceExpiryDate)
        }
      }
    }
  }

  private fun getCalculationsByDate(sentences: List<CalculableSentence>, futureData: TimelineFutureData): Map<LocalDate, List<TimelineCalculationDate>> {
    var allCalculations = (
      sentences.flatMap { it.sentenceParts() }.map { TimelineCalculationDate(it.sentencedAt, TimelineCalculationType.SENTENCED) } +
        futureData.additional.map {
          TimelineCalculationDate(
            it.appliesToSentencesFrom,
            TimelineCalculationType.ADDITIONAL_DAYS,
          )
        } +
        futureData.restored.map {
          TimelineCalculationDate(
            it.appliesToSentencesFrom,
            TimelineCalculationType.RESTORATION_DAYS,
          )
        } +
        futureData.ual.map { TimelineCalculationDate(it.appliesToSentencesFrom, TimelineCalculationType.UAL) }
      ) +
      TimelineCalculationDate(trancheConfiguration.trancheOneCommencementDate, TimelineCalculationType.TRANCHE_1) +
      TimelineCalculationDate(trancheConfiguration.trancheTwoCommencementDate, TimelineCalculationType.TRANCHE_2)

    allCalculations = allCalculations.sortedBy { it.date }

    return allCalculations.groupBy { it.date }
  }

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}
