package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.FeatureToggles
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
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ExternalMovement
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ExternalMovementDirection
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offender
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceGroup
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.SDSEarlyReleaseDefaultingRulesService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.WorkingDayService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.handlers.TimelineAwardedAdjustmentCalculationHandler
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.handlers.TimelineCalculationHandler
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.handlers.TimelineExternalAdmissionMovementCalculationHandler
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.handlers.TimelineExternalReleaseMovementCalculationHandler
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.handlers.TimelineSentenceCalculationHandler
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.handlers.TimelineTrancheCalculationHandler
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.handlers.TimelineTrancheThreeCalculationHandler
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.handlers.TimelineUalAdjustmentCalculationHandler
import java.time.LocalDate

@Service
class BookingTimelineService(
  private val workingDayService: WorkingDayService,
  private val trancheConfiguration: SDS40TrancheConfiguration,
  private val sdsEarlyReleaseDefaultingRulesService: SDSEarlyReleaseDefaultingRulesService,
  private val timelineCalculator: TimelineCalculator,
  private val timelineAwardedAdjustmentCalculationHandler: TimelineAwardedAdjustmentCalculationHandler,
  private val timelineTrancheCalculationHandler: TimelineTrancheCalculationHandler,
  private val trancheThreeCalculationHandler: TimelineTrancheThreeCalculationHandler,
  private val timelineSentenceCalculationHandler: TimelineSentenceCalculationHandler,
  private val timelineUalAdjustmentCalculationHandler: TimelineUalAdjustmentCalculationHandler,
  private val timelineExternalReleaseMovementCalculationHandler: TimelineExternalReleaseMovementCalculationHandler,
  private val timelineExternalAdmissionMovementCalculationHandler: TimelineExternalAdmissionMovementCalculationHandler,
  private val timelineAdjustmentService: TimelineAdjustmentService,
  private val featureToggles: FeatureToggles,
) {

  fun calculate(
    sentences: List<CalculableSentence>,
    adjustments: Adjustments,
    offender: Offender,
    returnToCustodyDate: LocalDate?,
    options: CalculationOptions,
    externalMovements: List<ExternalMovement>,
  ): CalculationOutput {
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

    val calculationsByDate = getCalculationsByDate(sentences, futureData, externalMovements)

    val earliestSentence = futureData.sentences.minBy { it.sentencedAt }

    val timelineTrackingData = TimelineTrackingData(
      futureData,
      calculationsByDate,
      latestRelease = earliestSentence.sentencedAt to earliestSentence,
      returnToCustodyDate,
      offender,
      options,
    )

    calculationsByDate.forEach { (timelineCalculationDate, calculations) ->
      checkForReleasesAndLicenseExpiry(timelineCalculationDate, timelineTrackingData)

      val skipCalculation = calculations.sortedBy { it.type.ordinal }
        .map {
          val result = handlerFor(it.type).handle(timelineCalculationDate, timelineTrackingData)
          result.skipCalculation
        }.all { it }

      if (!skipCalculation) {
        calculateLatestCustodialRelease(timelineTrackingData)
      }
    }

    return calculateFinalReleaseDatesAfterTimeline(timelineTrackingData, adjustments)
  }

  private fun calculateFinalReleaseDatesAfterTimeline(
    timelineTrackingData: TimelineTrackingData,
    adjustments: Adjustments,
  ): CalculationOutput {
    with(timelineTrackingData) {
      if (currentSentenceGroup.isNotEmpty()) {
        releasedSentenceGroups.add(
          SentenceGroup(
            currentSentenceGroup.minOf { it.sentencedAt },
            latestRelease.first,
            currentSentenceGroup.toList(),
          ),
        )
        currentSentenceGroup.clear()
      }
      latestCalculation =
        timelineCalculator.getLatestCalculation(
          releasedSentenceGroups.map { it.sentences }, offender, returnToCustodyDate,
        ).copy(
          sdsEarlyReleaseAllocatedTranche = trancheAndCommencement.first,
          sdsEarlyReleaseTranche = trancheAndCommencement.first,
        )

      if (beforeTrancheCalculation != null) {
        latestCalculation = sdsEarlyReleaseDefaultingRulesService.applySDSEarlyReleaseRulesAndFinalizeDates(
          latestCalculation,
          beforeTrancheCalculation!!,
          trancheAndCommencement.second!!,
          trancheAndCommencement.first,
          releasedSentenceGroups.flatMap { it.sentences },
        )

        latestCalculation = timelineAdjustmentService.applyTrancheAdjustmentLogic(latestCalculation, adjustments, trancheAndCommencement)
      }

      return CalculationOutput(
        releasedSentenceGroups.flatMap { it.sentences },
        releasedSentenceGroups,
        latestCalculation,
      )
    }
  }

  private fun handlerFor(type: TimelineCalculationType): TimelineCalculationHandler {
    return when (type) {
      TimelineCalculationType.SENTENCED -> timelineSentenceCalculationHandler
      TimelineCalculationType.ADDITIONAL_DAYS, TimelineCalculationType.RESTORATION_DAYS -> timelineAwardedAdjustmentCalculationHandler
      TimelineCalculationType.UAL -> timelineUalAdjustmentCalculationHandler
      TimelineCalculationType.TRANCHE_1,
      TimelineCalculationType.TRANCHE_2,
      -> timelineTrancheCalculationHandler
      TimelineCalculationType.TRANCHE_3 -> trancheThreeCalculationHandler
      TimelineCalculationType.EXTERNAL_ADMISSION -> timelineExternalAdmissionMovementCalculationHandler
      TimelineCalculationType.EXTERNAL_RELEASE -> timelineExternalReleaseMovementCalculationHandler
    }
  }

  private fun calculateLatestCustodialRelease(timelineTrackingData: TimelineTrackingData) {
    with(timelineTrackingData) {
      if (currentSentenceGroup.isNotEmpty()) {
        val latestReleaseSentence = currentSentenceGroup.maxBy { it.sentenceCalculation.adjustedDeterminateReleaseDate }
        val releaseDate =
          if (beforeTrancheCalculation != null && latestReleaseSentence.sentenceCalculation.adjustedDeterminateReleaseDate.isBefore(
              trancheAndCommencement.second!!,
            )
          ) {
            trancheAndCommencement.second!!
          } else {
            latestReleaseSentence.sentenceCalculation.adjustedDeterminateReleaseDate
          }
        latestRelease = maxOf(latestReleaseSentence.sentencedAt, workingDayService.previousWorkingDay(releaseDate).date) to latestReleaseSentence
      }
    }
  }

  private fun checkForReleasesAndLicenseExpiry(date: LocalDate, timelineTrackingData: TimelineTrackingData) {
    with(timelineTrackingData) {
      if (date.isAfter(latestRelease.first)) {
        if (currentSentenceGroup.isNotEmpty()) {
          // Release has happened. do extraction here.
          releasedSentenceGroups.add(
            SentenceGroup(
              currentSentenceGroup.minOf { it.sentencedAt },
              latestRelease.first,
              currentSentenceGroup.toList(),
            ),
          )
          currentSentenceGroup.forEach {
            if (it.sentenceCalculation.licenceExpiryDate?.isAfter(date) == true) {
              licenseSentences.add(it)
            }
          }
          currentSentenceGroup.clear()
        }
        latestCalculation = timelineCalculator.getLatestCalculation(releasedSentenceGroups.map { it.sentences }, offender, returnToCustodyDate)
      }
      if (licenseSentences.isNotEmpty()) {
        licenseSentences.removeIf {
          date.isAfter(it.sentenceCalculation.licenceExpiryDate)
        }
      }
    }
  }

  private fun getCalculationsByDate(sentences: List<CalculableSentence>, futureData: TimelineFutureData, externalMovements: List<ExternalMovement>): Map<LocalDate, List<TimelineCalculationDate>> {
    return (
      sentences.flatMap { it.sentenceParts().map { part -> TimelineCalculationDate(part.sentencedAt, TimelineCalculationType.SENTENCED) } } +
        futureData.additional.map { TimelineCalculationDate(it.appliesToSentencesFrom, TimelineCalculationType.ADDITIONAL_DAYS) } +
        futureData.restored.map { TimelineCalculationDate(it.appliesToSentencesFrom, TimelineCalculationType.RESTORATION_DAYS) } +
        futureData.ual.map { TimelineCalculationDate(it.appliesToSentencesFrom, TimelineCalculationType.UAL) } +
        listOf(
          TimelineCalculationDate(trancheConfiguration.trancheOneCommencementDate, TimelineCalculationType.TRANCHE_1),
          TimelineCalculationDate(trancheConfiguration.trancheTwoCommencementDate, TimelineCalculationType.TRANCHE_2),
          TimelineCalculationDate(trancheConfiguration.trancheThreeCommencementDate, TimelineCalculationType.TRANCHE_3),
        ) +
        externalMovements.map { TimelineCalculationDate(it.movementDate, if (it.direction == ExternalMovementDirection.OUT) TimelineCalculationType.EXTERNAL_RELEASE else TimelineCalculationType.EXTERNAL_ADMISSION) }
      )
      .sortedBy { it.date }
      .distinct()
      .groupBy { it.date }
  }

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}
