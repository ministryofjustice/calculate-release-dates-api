package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config

import org.springframework.boot.context.properties.ConfigurationProperties
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceIdentificationTrack
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AbstractSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculableSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RecallType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SDSEarlyReleaseExclusionType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.StandardDeterminateSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.ReleaseMultiplier
import java.time.LocalDate

@ConfigurationProperties(prefix = "early-release-configuration")
data class EarlyReleaseConfigurations(
  val configurations: List<EarlyReleaseConfiguration>,
)

data class EarlyReleaseConfiguration(
  val releaseMultiplier: Map<SentenceIdentificationTrack, ReleaseMultiplier>? = null,
  val recallCalculation: RecallCalculationType? = null,
  val filter: EarlyReleaseSentenceFilter,
  val additionsAppliedAfterDefaulting: Boolean = false,
  val tranches: List<EarlyReleaseTrancheConfiguration>,
) {
  fun matchesFilter(part: AbstractSentence): Boolean = when (filter) {
    EarlyReleaseSentenceFilter.SDS_40_EXCLUSIONS -> matchesSDS40Filter(part)
    EarlyReleaseSentenceFilter.SDS_OR_SDS_PLUS_ADULT -> matchesSDSOrSDSPlusAdult(part)
    EarlyReleaseSentenceFilter.FTR_56 -> part.recallType == RecallType.FIXED_TERM_RECALL_56
  }

  private fun matchesSDSOrSDSPlusAdult(sentence: AbstractSentence): Boolean = sentence is StandardDeterminateSentence &&
    !sentence.section250 &&
    listOf(SentenceIdentificationTrack.SDS, SentenceIdentificationTrack.SDS_PLUS).contains(sentence.identificationTrack)

  private fun matchesSDS40Filter(sentence: AbstractSentence): Boolean = sentence is StandardDeterminateSentence &&
    sentence.identificationTrack == SentenceIdentificationTrack.SDS &&
    (sentence.hasAnSDSEarlyReleaseExclusion == SDSEarlyReleaseExclusionType.NO || sentence.hasAnSDSEarlyReleaseExclusion.trancheThreeExclusion)

  fun earliestTranche() = tranches.minOf { it.date }

  fun isEligibleForTrancheRules(sentence: CalculableSentence): Boolean = if (this.releaseMultiplier != null) {
    this.releaseMultiplier.keys.contains(sentence.identificationTrack)
  } else {
    sentence.sentenceParts().any { this.matchesFilter(it) }
  }

  fun releaseDateConsidered(
    sentenceCalculation: SentenceCalculation,
  ): LocalDate = if (releaseMultiplier != null) {
    sentenceCalculation.adjustedDeterminateReleaseDate
  } else {
    sentenceCalculation.releaseDate
  }

  fun modifiesRecallReleaseDate(): Boolean = releaseMultiplier == null
}

enum class RecallCalculationType {
  FTR_56,
}
