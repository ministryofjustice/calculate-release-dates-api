package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.handlers

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.EarlyReleaseConfigurations
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AbstractSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculableSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceAdjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.UnadjustedReleaseDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.sentence.SentenceCombinationService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineCalculator
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineHandleResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineTrackingData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.getSentencePartIdentifiers
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Service
class TimelineSentenceCalculationHandler(
  timelineCalculator: TimelineCalculator,
  earlyReleaseConfigurations: EarlyReleaseConfigurations,
  private val sentenceCombinationService: SentenceCombinationService,
) : TimelineCalculationHandler(timelineCalculator, earlyReleaseConfigurations) {

  override fun handle(
    timelineCalculationDate: LocalDate,
    timelineTrackingData: TimelineTrackingData,
  ): TimelineHandleResult {
    with(timelineTrackingData) {
      allocatedEarlyRelease = earlyReleaseConfigurations.configurations.filter { timelineCalculationDate.isAfter(it.earliestTranche()) }.maxByOrNull { it.earliestTranche() }

      var servedAdas = findServedAdas(timelineCalculationDate, currentSentenceGroup, latestRelease)

      val existingAdjustments =
        currentSentenceGroup.maxByOrNull { it.sentenceCalculation.adjustments.totalAdjustmentDays() }?.sentenceCalculation?.adjustments ?: SentenceAdjustments()

      val newlySentenced = futureData.sentences.filter { it.sentencedAt == timelineCalculationDate }
      if (newlySentencedIsConsecutiveToAnyExistingSentences(timelineTrackingData, newlySentenced)) {
        servedAdas = 0
      }

      futureData.sentences -= newlySentenced

      val sentencesBeforeCombining = currentSentenceGroup.toList() + newlySentenced
      val sentencesAfterCombining = sentenceCombinationService.getSentencesToCalculate(sentencesBeforeCombining, offender)
      val combinedSentencesWhichHaveNewParts = sentencesAfterCombining.filter { it.sentenceParts().any { part -> newlySentenced.contains(part) } }
      val unchangedByCombiningSentences = currentSentenceGroup.filter { original ->
        sentencesAfterCombining.any { after -> after.getSentencePartIdentifiers() == original.getSentencePartIdentifiers() }
      }
      currentSentenceGroup.clear()
      currentSentenceGroup.addAll(combinedSentencesWhichHaveNewParts)
      currentSentenceGroup.addAll(unchangedByCombiningSentences)
      currentSentenceGroup.apply { sortWith(compareBy { it.sentencedAt }) }

      initialiseCalculationForNewSentences(timelineCalculationDate, timelineTrackingData, combinedSentencesWhichHaveNewParts)

      shareAdjustmentsFromExistingCustodialSentencesToNewlySentenced(combinedSentencesWhichHaveNewParts, existingAdjustments, servedAdas)

      applyUalToCombinedSentences(combinedSentencesWhichHaveNewParts, sentencesBeforeCombining)

      shareDeductionsThatAreApplicableToThisSentenceDate(timelineCalculationDate, timelineTrackingData)
    }
    return TimelineHandleResult()
  }

  private fun newlySentencedIsConsecutiveToAnyExistingSentences(timelineTrackingData: TimelineTrackingData, newlySentenced: List<AbstractSentence>): Boolean {
    with(timelineTrackingData) {
      return newlySentenced.any { new ->
        new.consecutiveSentenceUUIDs.isNotEmpty() &&
          currentSentenceGroup.any { existing ->
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
            multiplierFnForDate(timelineCalculationDate, allocatedTranche?.date, offender),
            historicMultiplierFnForDate(offender),
            findRecallCalculation(timelineCalculationDate, allocatedEarlyRelease),
            returnToCustodyDate,
          ),
          SentenceAdjustments(),
          calculateErsed = options.calculateErsed,
        )
        sentence.sentenceCalculation.allocatedEarlyRelease = allocatedEarlyRelease
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
        servedAdaDays = if (servedAdas == 0L) existingAdjustments.servedAdaDays else servedAdas,
      ),
    )
  }

  // Previous UAL does not apply to new sentences. However if the new sentence is part of a consec chain that already has UAL, it needs to be applied.
  private fun applyUalToCombinedSentences(
    newSentencesToCalculate: List<CalculableSentence>,
    sentencesBeforeCombining: List<CalculableSentence>,
  ) {
    newSentencesToCalculate.filter { it.sentenceParts().size > 1 }
      .forEach { combined ->
        val matchingSentenceBeforeMerging = sentencesBeforeCombining.find { it.sentenceParts().any { part -> combined.sentenceParts().contains(part) } }
        if (matchingSentenceBeforeMerging != null && matchingSentenceBeforeMerging.isCalculationInitialised()) {
          val ualAdjustments = matchingSentenceBeforeMerging.sentenceCalculation.adjustments
          timelineCalculator.setAdjustments(
            listOf(combined),
            SentenceAdjustments(
              ualDuringCustody = ualAdjustments.ualDuringCustody,
            ),
          )
        }
      }
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
