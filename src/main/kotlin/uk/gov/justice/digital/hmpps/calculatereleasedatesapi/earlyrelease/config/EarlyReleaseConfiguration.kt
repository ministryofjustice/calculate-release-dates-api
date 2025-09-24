package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config

import org.springframework.boot.context.properties.ConfigurationProperties
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceIdentificationTrack
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AbstractSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculableSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RecallType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SDSEarlyReleaseExclusionType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.StandardDeterminateSentence
import java.time.LocalDate

@ConfigurationProperties(prefix = "early-release-configuration")
data class EarlyReleaseConfigurations(
  val configurations: List<EarlyReleaseConfiguration>,
)

data class EarlyReleaseConfiguration(
  val releaseMultiplier: Map<SentenceIdentificationTrack, EarlyReleaseMultiplier>? = null,
  val recallCalculation: RecallCalculationType? = null,
  val filter: EarlyReleaseSentenceFilter,
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

  fun sentencesWithReleaseAfterTrancheCommencement(sentences: List<CalculableSentence>, earlyReleaseTrancheConfiguration: EarlyReleaseTrancheConfiguration? = null): List<CalculableSentence> = sentences.filter {
    releaseDateConsidered(it.sentenceCalculation).isAfter(earlyReleaseTrancheConfiguration?.date ?: earliestTranche())
  }

  fun releaseDateConsidered(
    sentenceCalculation: SentenceCalculation,
  ): LocalDate = if (releaseMultiplier != null) {
    sentenceCalculation.adjustedDeterminateReleaseDate
  } else {
    sentenceCalculation.releaseDate
  }
}

/*
  Early release multiplier defined as fraction.
   e.g. 1/3 1/2
   or if a decimal, denominator is 1 e.g 0.4/1 = 0.4
 */
data class EarlyReleaseMultiplier(
  val numerator: Double,
  val denominator: Double = 1.toDouble(),
) {
  fun toDouble(): Double = numerator.div(denominator)
}

enum class RecallCalculationType {
  FTR_56,
}
