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
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationRule
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationRule.ADJUSTED_AFTER_TRANCHE_COMMENCEMENT
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SDSEarlyReleaseTranche
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Adjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculableSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationOptions
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationOutput
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationResult
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
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.isAfterOrEqualTo
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

      calculations.sortedBy { it.type.ordinal }.forEachIndexed { _, it ->
        val result = handlerFor(it.type).handle(timelineCalculationDate, timelineTrackingData)
        if (result.skipCalculation) {
          return@forEach
        }
      }

      calculateLatestCustodialRelease(timelineTrackingData)
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
        timelineCalculator.getLatestCalculation(releasedSentenceGroups.map { it.sentences }, offender).copy(
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

        if (featureToggles.adjustmentsAfterTrancheEnabled) {
          latestCalculation = applyTrancheAdjustmentLogic(latestCalculation, adjustments, trancheAndCommencement)
        }
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
        latestCalculation = timelineCalculator.getLatestCalculation(releasedSentenceGroups.map { it.sentences }, offender)
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

  private fun applyTrancheAdjustmentLogic(
    calculation: CalculationResult,
    adjustments: Adjustments,
    trancheAndCommencement: Pair<SDSEarlyReleaseTranche, LocalDate?>,
  ): CalculationResult {
    if (trancheAndCommencement.first !in listOf(
        SDSEarlyReleaseTranche.TRANCHE_1,
        SDSEarlyReleaseTranche.TRANCHE_2,
      )
    ) {
      log.info("No adjustments to apply as tranche is ${calculation.sdsEarlyReleaseTranche}")
      return calculation
    }

    return adjustReleaseDatesForTranche(calculation, adjustments, trancheAndCommencement.second!!)
  }

  private fun adjustReleaseDatesForTranche(
    calculation: CalculationResult,
    adjustments: Adjustments,
    commencementDate: LocalDate,
  ): CalculationResult {
    val rulesToHandle = listOf(
      CalculationRule.SDS_EARLY_RELEASE_ADJUSTED_TO_TRANCHE_1_COMMENCEMENT,
      CalculationRule.SDS_EARLY_RELEASE_ADJUSTED_TO_TRANCHE_2_COMMENCEMENT,
      CalculationRule.SDS_STANDARD_RELEASE_APPLIES,
    )

    val releaseDateTypesToAdjust = listOf(
      ReleaseDateType.HDCED,
      ReleaseDateType.ERSED,
      ReleaseDateType.PED,
    )

    var updatedCalculation = calculation

    for (rule in rulesToHandle) {
      for (releaseDateType in releaseDateTypesToAdjust) {
        updatedCalculation = adjustReleaseDateForType(
          updatedCalculation,
          adjustments,
          commencementDate,
          releaseDateType,
          rule,
        )
      }
    }

    return updatedCalculation
  }

  private fun adjustReleaseDateForType(
    calculation: CalculationResult,
    adjustments: Adjustments,
    commencementDate: LocalDate,
    releaseDateType: ReleaseDateType,
    rule: CalculationRule,
  ): CalculationResult {
    val applicableRules = calculation.breakdownByReleaseDateType[releaseDateType]?.rules

    if (applicableRules?.contains(rule) == true) {
      log.info("${releaseDateType.name} before adjustment: ${calculation.dates[releaseDateType]}")
      val crd = calculation.dates[ReleaseDateType.CRD]!!

      val numberOfDaysToAdjust = calculateAdjustmentDaysAfterCommencement(adjustments, commencementDate, crd)

      if (numberOfDaysToAdjust != 0L) {
        return applyAdjustment(
          calculation,
          releaseDateType,
          commencementDate,
          rule,
          numberOfDaysToAdjust,
        )
      }
    }

    return calculation
  }

  private fun applyAdjustment(
    calculation: CalculationResult,
    releaseDateType: ReleaseDateType,
    commencementDate: LocalDate,
    rule: CalculationRule,
    numberOfDaysToAdjust: Long,
  ): CalculationResult {
    var updatedCalculation = calculation.copy(
      dates = calculation.dates.plus(
        releaseDateType to calculation.dates[releaseDateType]!!.plusDays(numberOfDaysToAdjust),
      ),
    )
    log.info("$releaseDateType after adjustment: ${updatedCalculation.dates[releaseDateType]}")

    if (updatedCalculation.dates[releaseDateType]?.isBefore(commencementDate) == true &&
      rule in listOf(
        CalculationRule.SDS_EARLY_RELEASE_ADJUSTED_TO_TRANCHE_1_COMMENCEMENT,
        CalculationRule.SDS_EARLY_RELEASE_ADJUSTED_TO_TRANCHE_2_COMMENCEMENT,
      )
    ) {
      updatedCalculation = updatedCalculation.copy(
        dates = updatedCalculation.dates.plus(
          releaseDateType to commencementDate,
        ),
      )
    }

    return updateBreakdownWithTrancheAdjustedRule(updatedCalculation, releaseDateType)
  }

  private fun calculateAdjustmentDaysAfterCommencement(
    adjustments: Adjustments,
    commencementDate: LocalDate,
    crd: LocalDate,
  ): Long {
    val ualDays = adjustments.getOrEmptyList(UNLAWFULLY_AT_LARGE)
      .filter { it.appliesToSentencesFrom.isAfterOrEqualTo(commencementDate) && it.appliesToSentencesFrom.isBefore(crd) }
      .sumOf { it.numberOfDays }

    val adaDays = adjustments.getOrEmptyList(ADDITIONAL_DAYS_AWARDED)
      .filter { it.appliesToSentencesFrom.isAfterOrEqualTo(commencementDate) && it.appliesToSentencesFrom.isBefore(crd) }
      .sumOf { it.numberOfDays }

    val radaDays = adjustments.getOrEmptyList(RESTORATION_OF_ADDITIONAL_DAYS_AWARDED)
      .filter { it.appliesToSentencesFrom.isAfterOrEqualTo(commencementDate) && it.appliesToSentencesFrom.isBefore(crd) }
      .sumOf { it.numberOfDays }

    val totalAdjustmentsToApply = ualDays + adaDays - radaDays
    log.info("Total adjustments to apply $totalAdjustmentsToApply for commencement date $commencementDate (UAL $ualDays + ADAs $adaDays + RADA $radaDays)")

    return totalAdjustmentsToApply.toLong()
  }

  private fun updateBreakdownWithTrancheAdjustedRule(
    calculation: CalculationResult,
    releaseDateType: ReleaseDateType,
  ): CalculationResult {
    val existingBreakdown = calculation.breakdownByReleaseDateType[releaseDateType]
    return calculation.copy(
      breakdownByReleaseDateType = calculation.breakdownByReleaseDateType.plus(
        releaseDateType to existingBreakdown!!.copy(
          rules = existingBreakdown.rules.plus(ADJUSTED_AFTER_TRANCHE_COMMENCEMENT),
        ),
      ),
    )
  }

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}
