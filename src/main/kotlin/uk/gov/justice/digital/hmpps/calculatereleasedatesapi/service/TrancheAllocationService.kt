package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.LegislationWithTranches
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.TrancheConfiguration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.TrancheType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AFineSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculableSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ConsecutiveSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineTrackingData
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Service
class TrancheAllocationService {

  fun allocateTranche(timelineTrackingData: TimelineTrackingData, legislation: LegislationWithTranches): TrancheConfiguration? {
    if (!legislation.trancheSelectionStrategy.hasSentencesThatMightApplyToTheTranche(timelineTrackingData, legislation)) {
      return null
    }

    if (
      timelineTrackingData.applicableSdsLegislations.hasTrancheSet(legislation.legislationName) ||
      timelineTrackingData.applicableFtrLegislation?.legislation?.legislationName == legislation.legislationName
    ) {
      // Already allocated to a tranche in this early release group.
      return null
    }

    val allocatedTranche = findTranche(timelineTrackingData, legislation)
    if (legislation.anyReasonTheTrancheCannotApply(allocatedTranche, timelineTrackingData)) {
      return null
    }
    return allocatedTranche
  }

  private fun findTranche(timelineTrackingData: TimelineTrackingData, legislation: LegislationWithTranches): TrancheConfiguration {
    val allSentences = legislation.trancheSelectionStrategy.sentencesToMatchOnSentenceLength(timelineTrackingData, legislation)
    val allocatedTranche = legislation.tranches.find {
      when (it.type) {
        TrancheType.SENTENCE_LENGTH -> matchesSentenceLength(allSentences, legislation.commencementDate(), it)
        TrancheType.FINAL -> true // Not matched any other tranche, so must be in this one.
      }
    }
    return requireNotNull(allocatedTranche) { "Should have allocated a specific tranche or the final one" }
  }

  private fun matchesSentenceLength(
    sentencesConsideredForTrancheRules: List<CalculableSentence>,
    legislationCommencementDate: LocalDate,
    tranche: TrancheConfiguration,
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
