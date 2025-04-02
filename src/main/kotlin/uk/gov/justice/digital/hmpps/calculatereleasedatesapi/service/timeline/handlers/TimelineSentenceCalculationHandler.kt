package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.handlers

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.ReleasePointMultipliersConfiguration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.SDS40TrancheConfiguration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AbstractSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculableSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceAdjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.UnadjustedReleaseDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.SentenceCombinationService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineCalculator
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineHandleResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineTrackingData
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

@Service
class TimelineSentenceCalculationHandler(
  trancheConfiguration: SDS40TrancheConfiguration,
  releasePointConfiguration: ReleasePointMultipliersConfiguration,
  timelineCalculator: TimelineCalculator,
  private val sentenceCombinationService: SentenceCombinationService,
) : TimelineCalculationHandler(trancheConfiguration, releasePointConfiguration, timelineCalculator) {

  override fun handle(
    timelineCalculationDate: LocalDate,
    timelineTrackingData: TimelineTrackingData,
  ): TimelineHandleResult {
    with(timelineTrackingData) {
      inPrison = true

      var servedAdas = findServedAdas(timelineCalculationDate, currentSentenceGroup, latestRelease)

      val existingAdjustments =
        currentSentenceGroup.maxByOrNull { abs(it.sentenceCalculation.adjustments.adjustmentsForInitialRelease()) }?.sentenceCalculation?.adjustments ?: SentenceAdjustments()

      val newlySentenced = futureData.sentences.filter { it.sentencedAt == timelineCalculationDate }
      if (newlySentencedIsConsecutiveToAnyExistingSentences(timelineTrackingData, newlySentenced)) {
        servedAdas = 0
      }

      currentSentenceGroup.addAll(newlySentenced)
      futureData.sentences -= newlySentenced

      val afterCombination = sentenceCombinationService.getSentencesToCalculate(currentSentenceGroup, offender)

      val newSentencesToCalculate = afterCombination.filter { it.sentenceParts().any { part -> newlySentenced.contains(part) } }
      val newSentencesWithParts = newSentencesToCalculate.flatMap { it.sentenceParts() }
      val original = currentSentenceGroup.filter { it.sentenceParts().none { part -> newSentencesWithParts.contains(part) } }

      currentSentenceGroup.clear()
      currentSentenceGroup.addAll(newSentencesToCalculate)
      currentSentenceGroup.addAll(original)

      initialiseCalculationForNewSentences(timelineCalculationDate, timelineTrackingData, newSentencesToCalculate)

      shareAdjustmentsFromExistingCustodialSentencesToNewlySentenced(newSentencesToCalculate, existingAdjustments, servedAdas)

//      handleNewlySentencedPartOfConsecutiveSentence(timelineCalculationDate, timelineTrackingData)

      shareDeductionsThatAreApplicableToThisSentenceDate(timelineCalculationDate, timelineTrackingData)
    }
    return TimelineHandleResult()
  }

  private fun newlySentencedIsConsecutiveToAnyExistingSentences(timelineTrackingData: TimelineTrackingData, newlySentenced: List<AbstractSentence>): Boolean {
    with(timelineTrackingData) {
      return newlySentenced.any { new ->
        new.consecutiveSentenceUUIDs.isNotEmpty() && currentSentenceGroup.any { existing ->
          existing.sentenceParts().map { it.identifier }.contains(new.consecutiveSentenceUUIDs[0])
        }
      }
    }
  }

  private fun initialiseCalculationForNewSentences(timelineCalculationDate: LocalDate, timelineTrackingData: TimelineTrackingData, newSentencesToCalculate: List<CalculableSentence>) {
    with(timelineTrackingData) {
      newSentencesToCalculate.forEach { sentence ->
        sentence.sentenceCalculation = SentenceCalculation(
          UnadjustedReleaseDate(
            sentence,
            multiplierFnForDate(timelineCalculationDate, trancheAndCommencement.second),
            historicMultiplierFnForDate(),
            returnToCustodyDate,
          ),
          SentenceAdjustments(),
          calculateErsed = options.calculateErsed,
        )
      }
    }
  }

  private fun handleNewlySentencedPartOfConsecutiveSentence(timelineCalculationDate: LocalDate, timelineTrackingData: TimelineTrackingData) {
    with(timelineTrackingData) {
      // This new sentence is part of a consecutive sentence starting on an earlier date.
      currentSentenceGroup.filter {
        it.sentencedAt != timelineCalculationDate && it.sentenceParts()
          .any { part -> part.sentencedAt == timelineCalculationDate }
      }
        .forEach {
          it.sentenceCalculation.unadjustedReleaseDate.findMultiplierByIdentificationTrack =
            multiplierFnForDate(timelineCalculationDate.minusDays(1), trancheAndCommencement.second) // Use day before sentencing for when someone is sentenced for another consecutive part on tranche commencement.
        }
    }
  }

  private fun shareDeductionsThatAreApplicableToThisSentenceDate(
    timelineCalculationDate: LocalDate,
    timelineTrackingData: TimelineTrackingData,
  ) {
    with(timelineTrackingData) {
      val taggedBail = futureData.taggedBail.filter { it.appliesToSentencesFrom == timelineCalculationDate }
      val remand = futureData.remand.filter { it.appliesToSentencesFrom == timelineCalculationDate }
      val recallTaggedBail = futureData.recallTaggedBail.filter { it.appliesToSentencesFrom == timelineCalculationDate }
      val recallRemand = futureData.recallRemand.filter { it.appliesToSentencesFrom == timelineCalculationDate }
      timelineCalculator.setAdjustments(
        currentSentenceGroup,
        SentenceAdjustments(
          taggedBail = taggedBail.map { it.numberOfDays }.reduceOrNull { acc, it -> acc + it }?.toLong() ?: 0L,
          remand = remand.map { it.numberOfDays }.reduceOrNull { acc, it -> acc + it }?.toLong() ?: 0L,
          recallTaggedBail = recallTaggedBail.map { it.numberOfDays }.reduceOrNull { acc, it -> acc + it }?.toLong()
            ?: 0L,
          recallRemand = recallRemand.map { it.numberOfDays }.reduceOrNull { acc, it -> acc + it }?.toLong() ?: 0L,
          awardedDuringCustody = padas,
        ),
      )

      padas = 0
      futureData.taggedBail -= taggedBail
      futureData.remand -= remand
      futureData.recallTaggedBail -= recallTaggedBail
      futureData.recallRemand -= recallRemand
    }
  }

  private fun shareAdjustmentsFromExistingCustodialSentencesToNewlySentenced(
    newSentencesToCalculate: List<CalculableSentence>,
    existingAdjustments: SentenceAdjustments,
    servedAdas: Long,
  ) {
    timelineCalculator.setAdjustments(
      newSentencesToCalculate,
      SentenceAdjustments(
        taggedBail = existingAdjustments.taggedBail,
        remand = existingAdjustments.remand,
        recallRemand = existingAdjustments.recallRemand,
        recallTaggedBail = existingAdjustments.recallTaggedBail,
        awardedDuringCustody = existingAdjustments.awardedDuringCustody,
        servedAdaDays = servedAdas,
      ),
    )
  }

  private fun findServedAdas(
    sentenceDate: LocalDate,
    custodialSentences: MutableList<CalculableSentence>,
    latestRelease: Pair<LocalDate, CalculableSentence>,
  ): Long {
    if (latestRelease.second.isCalculationInitialised()) {
      val previousReleaseWithoutAdas = latestRelease.second.sentenceCalculation.releaseDateWithoutAwarded
      if (custodialSentences.isNotEmpty() && sentenceDate.isAfter(previousReleaseWithoutAdas)) {
        return ChronoUnit.DAYS.between(previousReleaseWithoutAdas, sentenceDate) - 1
      }
    }
    return 0
  }
}
