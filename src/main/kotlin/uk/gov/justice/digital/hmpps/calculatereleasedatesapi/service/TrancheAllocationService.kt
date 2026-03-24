package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.EarlyReleaseTrancheConfiguration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.EarlyReleaseTrancheType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.TranchedLegislation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AFineSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculableSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ConsecutiveSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineTrackingData
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Service
class TrancheAllocationService {

  fun allocateTranche(timelineTrackingData: TimelineTrackingData, tranchedLegislation: TranchedLegislation): EarlyReleaseTrancheConfiguration? {
    val trancheSelectionStrategy = tranchedLegislation.trancheSelectionStrategy
    if (!trancheSelectionStrategy.hasSentencesThatMightApplyToTheTranche(timelineTrackingData, tranchedLegislation.configuration)) {
      return null
    }

    if (timelineTrackingData.allocatedTranche != null && tranchedLegislation.configuration.tranches.any { it == timelineTrackingData.allocatedTranche }) {
      // Already allocated to a tranche in this early release group.
      return null
    }

    return findTranche(
      tranchedLegislation.configuration.tranches,
      tranchedLegislation.commencementDate(),
      trancheSelectionStrategy.sentencesToMatchOnSentenceLength(timelineTrackingData, tranchedLegislation.configuration),
    )
  }

  private fun findTranche(
    tranches: List<EarlyReleaseTrancheConfiguration>,
    legislationCommencementDate: LocalDate,
    allSentences: List<CalculableSentence>,
  ): EarlyReleaseTrancheConfiguration? = tranches.find {
    when (it.type) {
      EarlyReleaseTrancheType.SENTENCE_LENGTH -> matchesSentenceLength(allSentences, legislationCommencementDate, it)
      EarlyReleaseTrancheType.FINAL -> true // Not matched any other tranche, so must be in this one.
      EarlyReleaseTrancheType.SDS_40_TRANCHE_3 -> false // Not really a tranche that can be allocated to.
    }
  }

  private fun matchesSentenceLength(
    sentencesConsideredForTrancheRules: List<CalculableSentence>,
    legislationCommencementDate: LocalDate,
    tranche: EarlyReleaseTrancheConfiguration,
  ): Boolean {
    val sentenceDurations = sentencesConsideredForTrancheRules.map { filterAndMapSentencesForNotIncludedTypesByDuration(it, legislationCommencementDate, tranche.unit!!) }
    return sentenceDurations.none { it >= tranche.duration!! }
  }

  private fun filterAndMapSentencesForNotIncludedTypesByDuration(
    sentenceToFilter: CalculableSentence,
    legislationCommencementDate: LocalDate,
    unit: ChronoUnit,
  ): Long {
    return if (sentenceToFilter is ConsecutiveSentence) {
      val filteredSentences = sentenceToFilter.orderedSentences
        .filter { filterOnType(it) && filterOnSentenceDate(it, legislationCommencementDate) }

      if (filteredSentences.isNotEmpty()) {
        val earliestSentencedAt = filteredSentences.minByOrNull { it.sentencedAt } ?: return 0
        var concurrentSentenceEndDate = earliestSentencedAt.sentencedAt

        filteredSentences.forEach { sentence ->
          concurrentSentenceEndDate = sentence.totalDuration()
            .getEndDate(concurrentSentenceEndDate).plusDays(1)
        }

        unit.between(earliestSentencedAt.sentencedAt, concurrentSentenceEndDate)
      } else {
        0
      }
    } else {
      if (filterOnType(sentenceToFilter) && filterOnSentenceDate(sentenceToFilter, legislationCommencementDate)) {
        unit.between(
          sentenceToFilter.sentencedAt,
          sentenceToFilter.sentencedAt.plus(sentenceToFilter.getLengthInDays().toLong(), ChronoUnit.DAYS).plusDays(1),
        )
      } else {
        0
      }
    }
  }

  private fun filterOnType(sentence: CalculableSentence): Boolean = !sentence.isDto() && !sentence.isOrExclusivelyBotus() && sentence !is AFineSentence

  private fun filterOnSentenceDate(sentence: CalculableSentence, legislationCommencementDate: LocalDate): Boolean = sentence.sentencedAt.isBefore(legislationCommencementDate)
}
