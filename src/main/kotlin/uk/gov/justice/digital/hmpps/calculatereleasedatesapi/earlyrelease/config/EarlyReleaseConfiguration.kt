package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config

import org.springframework.boot.context.properties.ConfigurationProperties
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceIdentificationTrack
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AbstractSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offender
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SDSEarlyReleaseExclusionType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.StandardDeterminateSentence

@ConfigurationProperties(prefix = "early-release-configuration")
data class EarlyReleaseConfigurations(
  val configurations: List<EarlyReleaseConfiguration>,
)

data class EarlyReleaseConfiguration(
  val releaseMultiplier: Map<SentenceIdentificationTrack, EarlyReleaseMultipler>,
  val filter: EarlyReleaseSentenceFilter,
  val tranches: List<EarlyReleaseTrancheConfiguration>,
) {
  fun matchesFilter(part: AbstractSentence, offender: Offender): Boolean = when (filter) {
    EarlyReleaseSentenceFilter.SDS_40_EXCLUSIONS -> matchesSDS40Filter(part)
    EarlyReleaseSentenceFilter.SDS_OR_SDS_PLUS_ADULT -> matchesSDSOrSDSPlusAdult(part, offender)
  }

  private fun matchesSDSOrSDSPlusAdult(sentence: AbstractSentence, offender: Offender): Boolean = sentence is StandardDeterminateSentence &&
    !offender.underEighteenAt(sentence.sentencedAt) &&
    listOf(SentenceIdentificationTrack.SDS, SentenceIdentificationTrack.SDS_PLUS).contains(sentence.identificationTrack)

  private fun matchesSDS40Filter(sentence: AbstractSentence): Boolean = sentence is StandardDeterminateSentence &&
    sentence.identificationTrack == SentenceIdentificationTrack.SDS &&
    (sentence.hasAnSDSEarlyReleaseExclusion == SDSEarlyReleaseExclusionType.NO || sentence.hasAnSDSEarlyReleaseExclusion.trancheThreeExclusion)

  fun earliestTranche() = tranches.minOf { it.date }
}

/*
  Early release multipler defined as fraction.
   e.g. 1/3 1/2
   or if a decimal, denominator is 1 e.g 0.4/1 = 0.4
 */
data class EarlyReleaseMultipler(
  val numerator: Double,
  val denominator: Double = 1.toDouble(),
) {
  fun toDouble(): Double = numerator.div(denominator)
}
