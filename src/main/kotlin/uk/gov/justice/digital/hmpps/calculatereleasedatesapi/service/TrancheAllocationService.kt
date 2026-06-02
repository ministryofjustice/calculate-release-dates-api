package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.LegislationWithTranches
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.TrancheConfiguration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.TrancheType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AFineSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculableSentence
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
        TrancheType.SENTENCE_LENGTH_LESS_THAN -> matchesSentenceLength(allSentences, legislation, it) { sentenceLength, trancheDuration -> sentenceLength >= trancheDuration }
        TrancheType.SENTENCE_LENGTH_LESS_THAN_OR_EQUAL_TO -> matchesSentenceLength(allSentences, legislation, it) { sentenceLength, trancheDuration -> sentenceLength > trancheDuration }
        TrancheType.FINAL -> true // Not matched any other tranche, so must be in this one.
      }
    }
    return requireNotNull(allocatedTranche) { "Should have allocated a specific tranche or the final one" }
  }

  private fun matchesSentenceLength(
    sentencesConsideredForTrancheRules: List<CalculableSentence>,
    legislation: LegislationWithTranches,
    tranche: TrancheConfiguration,
    isDurationTooLongForTranche: (sentenceLength: Long, trancheDuration: Int) -> Boolean,
  ): Boolean {
    val durations = sentenceDurationsInTheUnitOfTheTrancheDuration(sentencesConsideredForTrancheRules, legislation.commencementDate(), tranche.unit!!)
    return tranche.duration != null && durations.none { isDurationTooLongForTranche(it, tranche.duration) }
  }

  private fun sentenceDurationsInTheUnitOfTheTrancheDuration(
    sentences: List<CalculableSentence>,
    legislationCommencementDate: LocalDate,
    unit: ChronoUnit,
  ) = sentences.map {
    durationInUnit(legislationCommencementDate, unit, it.sentenceParts())
  }

  private fun durationInUnit(
    legislationCommencementDate: LocalDate,
    unit: ChronoUnit,
    sentences: List<CalculableSentence>,
  ): Long {
    val filteredSentences = sentences
      .filter { filterOnType(it) && filterOnSentenceDate(it, legislationCommencementDate) }

    if (filteredSentences.isEmpty()) return 0

    val earliestSentencedAt = filteredSentences.minByOrNull { it.sentencedAt }?.sentencedAt ?: return 0
    var concurrentSentenceEndDate = earliestSentencedAt

    filteredSentences.forEach {
      concurrentSentenceEndDate = it.totalDuration().getEndDate(concurrentSentenceEndDate).plusDays(1)
    }

    return unit.between(earliestSentencedAt, concurrentSentenceEndDate)
  }

  private fun filterOnType(sentence: CalculableSentence): Boolean = !sentence.isDto() && !sentence.isOrExclusivelyBotus() && sentence !is AFineSentence

  private fun filterOnSentenceDate(sentence: CalculableSentence, legislationCommencementDate: LocalDate): Boolean = sentence.sentencedAt.isBefore(legislationCommencementDate)
}
