package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.FTRLegislationConfiguration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.LegislationName
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.SDSLegislationConfiguration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType.ADDITIONAL_DAYS_AWARDED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType.RECALL_REMAND
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType.RECALL_TAGGED_BAIL
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType.REMAND
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType.RESTORATION_OF_ADDITIONAL_DAYS_AWARDED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType.TAGGED_BAIL
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType.UNLAWFULLY_AT_LARGE
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.TrancheName
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AbstractSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Adjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculableSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationOptions
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationOutput
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ExternalMovement
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offender
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceGroup
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.SDSEarlyReleaseDefaultingRulesService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.WorkingDayService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.handlers.TimelineAwardedAdjustmentCalculationHandler
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.handlers.TimelineCalculationHandler
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.handlers.TimelineExternalMovementCalculationHandler
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.handlers.TimelineFTR56TrancheCalculationHandler
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.handlers.TimelineSDSLegislationAmendmentHandler
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.handlers.TimelineSDSLegislationCommencementHandler
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.handlers.TimelineSDSTrancheCalculationHandler
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.handlers.TimelineSentenceCalculationHandler
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.handlers.TimelineUalAdjustmentCalculationHandler
import java.time.LocalDate

@Service
class BookingTimelineService(
  private val workingDayService: WorkingDayService,
  private val sdsEarlyReleaseDefaultingRulesService: SDSEarlyReleaseDefaultingRulesService,
  private val timelineCalculator: TimelineCalculator,
  private val timelineAwardedAdjustmentCalculationHandler: TimelineAwardedAdjustmentCalculationHandler,
  private val timelineSDSTrancheCalculationHandler: TimelineSDSTrancheCalculationHandler,
  private val timelineFTR56TrancheCalculationHandler: TimelineFTR56TrancheCalculationHandler,
  private val timelineSentenceCalculationHandler: TimelineSentenceCalculationHandler,
  private val timelineUalAdjustmentCalculationHandler: TimelineUalAdjustmentCalculationHandler,
  private val timelineExternalMovementCalculationHandler: TimelineExternalMovementCalculationHandler,
  private val timelinePostTrancheAdjustmentService: TimelinePostTrancheAdjustmentService,
  private val timelineSDSLegislationCommencementHandler: TimelineSDSLegislationCommencementHandler,
  private val timelineSDSLegislationAmendmentHandler: TimelineSDSLegislationAmendmentHandler,
  private val sdsLegislationConfiguration: SDSLegislationConfiguration,
  private val ftrLegislationConfiguration: FTRLegislationConfiguration,
) {

  fun calculate(
    sentences: List<AbstractSentence>,
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

    val externalMovementTimeline = ExternalMovementTimeline(externalMovements)

    val timelineTrackingData = TimelineTrackingData(
      futureData,
      calculationsByDate,
      latestRelease = earliestSentence.sentencedAt to earliestSentence,
      returnToCustodyDate,
      offender,
      options,
      externalMovementTimeline,
    )

    calculationsByDate.forEach { (timelineCalculationDate, calculations) ->
      checkForReleasesAndLicenceExpiry(timelineCalculationDate, timelineTrackingData)

      val results = calculations.sortedBy { it.type.ordinal }.map {
        timelineTrackingData.currentTimelineCalculationDate = it
        handlerFor(it.type).handle(timelineCalculationDate, timelineTrackingData)
      }
      val anyCalculationRequired = results.any { it.requiresCalculation }
      val anySkipCalculation = results.any { it.skipCalculationForEntireDate }

      if (anyCalculationRequired && !anySkipCalculation) {
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
          releasedSentenceGroups.map { it.sentences },
          offender,
          returnToCustodyDate,
          options,
        )

      if (beforeTrancheCalculation != null) {
        val allSentences = releasedSentenceGroups.flatMap { it.sentences }
        latestCalculation = sdsEarlyReleaseDefaultingRulesService.applySDSEarlyReleaseRulesAndFinalizeDates(
          latestCalculation,
          beforeTrancheCalculation!!,
          allSentences,
        )
        val earliestApplicableDate = beforeTrancheCalculation?.legislationApplied?.earliestApplicableDate
        if (earliestApplicableDate != null) {
          latestCalculation = timelinePostTrancheAdjustmentService.applyTrancheAdjustmentLogic(
            latestCalculation,
            adjustments,
            earliestApplicableDate,
          )
        }
      }

      val sds40TrancheName = timelineTrackingData.trancheAllocationByLegislationName[LegislationName.SDS_40] ?: TrancheName.TRANCHE_0
      return CalculationOutput(
        releasedSentenceGroups.flatMap { it.sentences },
        releasedSentenceGroups,
        latestCalculation.copy(
          trancheAllocationByLegislationName = timelineTrackingData.trancheAllocationByLegislationName,
          sdsEarlyReleaseTranche = sds40TrancheName,
          sdsEarlyReleaseAllocatedTranche = sds40TrancheName,
        ),
      )
    }
  }

  private fun handlerFor(type: TimelineCalculationType): TimelineCalculationHandler = when (type) {
    TimelineCalculationType.SDS_LEGISLATION_COMMENCEMENT -> timelineSDSLegislationCommencementHandler
    TimelineCalculationType.SENTENCED -> timelineSentenceCalculationHandler
    TimelineCalculationType.ADDITIONAL_DAYS, TimelineCalculationType.RESTORATION_DAYS -> timelineAwardedAdjustmentCalculationHandler
    TimelineCalculationType.UAL -> timelineUalAdjustmentCalculationHandler
    TimelineCalculationType.EARLY_RELEASE_TRANCHE -> timelineSDSTrancheCalculationHandler
    TimelineCalculationType.FTR56_TRANCHE -> timelineFTR56TrancheCalculationHandler
    TimelineCalculationType.EXTERNAL_MOVEMENT -> timelineExternalMovementCalculationHandler
    TimelineCalculationType.SDS_LEGISLATION_AMENDMENT -> timelineSDSLegislationAmendmentHandler
  }

  private fun calculateLatestCustodialRelease(timelineTrackingData: TimelineTrackingData) {
    with(timelineTrackingData) {
      if (currentSentenceGroup.isNotEmpty()) {
        val latestReleaseSentence = currentSentenceGroup.maxBy { it.sentenceCalculation.adjustedDeterminateReleaseDate }
        val earliestApplicableDateForLatestSentence = latestReleaseSentence.sentenceCalculation.applicableSdsLegislation?.earliestApplicableDate
        val releaseDate =
          if (earliestApplicableDateForLatestSentence != null && latestReleaseSentence.sentenceCalculation.adjustedDeterminateReleaseDate.isBefore(earliestApplicableDateForLatestSentence)) {
            earliestApplicableDateForLatestSentence
          } else {
            latestReleaseSentence.sentenceCalculation.adjustedDeterminateReleaseDate
          }
        latestRelease = maxOf(latestReleaseSentence.sentencedAt, workingDayService.previousWorkingDay(releaseDate).date) to latestReleaseSentence
      }
    }
  }

  private fun checkForReleasesAndLicenceExpiry(date: LocalDate, timelineTrackingData: TimelineTrackingData) {
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
            if (it.sentenceCalculation.licenceExpiryAtInitialRelease?.isAfter(date) == true) {
              licenceSentences.add(it)
            }
          }
          currentSentenceGroup.clear()
        }
        latestCalculation = timelineCalculator.getLatestCalculation(
          releasedSentenceGroups.map { it.sentences },
          offender,
          returnToCustodyDate,
          options,
        )
      }
      if (licenceSentences.isNotEmpty()) {
        val sentencesThatHaveExpired = licenceSentences.filter { date.isAfter(it.sentenceCalculation.licenceExpiryAtInitialRelease) }
        licenceSentences.removeAll(sentencesThatHaveExpired)
        expiredLicenceSentences.addAll(sentencesThatHaveExpired)
      }
    }
  }

  private fun getCalculationsByDate(sentences: List<CalculableSentence>, futureData: TimelineFutureData, externalMovements: List<ExternalMovement>): Map<LocalDate, List<TimelineCalculationDate>> = (
    sentences.flatMap { it.sentenceParts().map { part -> TimelineCalculationDate(part.sentencedAt, TimelineCalculationType.SENTENCED) } } +
      futureData.additional.map { TimelineCalculationDate(it.appliesToSentencesFrom, TimelineCalculationType.ADDITIONAL_DAYS) } +
      futureData.restored.map { TimelineCalculationDate(it.appliesToSentencesFrom, TimelineCalculationType.RESTORATION_DAYS) } +
      futureData.ual.map { TimelineCalculationDate(it.appliesToSentencesFrom, TimelineCalculationType.UAL) } +
      sdsLegislationConfiguration.all().flatMap { legislation -> legislation.requiredTimelineCalculations() } +
      ftrLegislationConfiguration.ftr56Legislation.requiredTimelineCalculations() +
      externalMovements.map { TimelineCalculationDate(it.movementDate, TimelineCalculationType.EXTERNAL_MOVEMENT) }
    )
    .sortedBy { it.date }
    .distinct()
    .groupBy { it.date }

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}
