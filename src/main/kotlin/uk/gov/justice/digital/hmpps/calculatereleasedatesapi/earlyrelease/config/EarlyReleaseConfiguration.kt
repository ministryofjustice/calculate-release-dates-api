package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config

import org.springframework.boot.context.properties.ConfigurationProperties
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceIdentificationTrack
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AbstractSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SDSEarlyReleaseExclusionType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.StandardDeterminateSentence

@ConfigurationProperties(prefix = "early-release-configuration")
data class EarlyReleaseConfigurations(
  val configurations: List<EarlyReleaseConfiguration>,
)

data class EarlyReleaseConfiguration(
  val releaseMultiplier: Double,
  val filter: EarlyReleaseSentenceFilter,
  val tranches: List<EarlyReleaseTrancheConfiguration>,
) {
  fun matchesFilter(part: AbstractSentence): Boolean = part.identificationTrack == SentenceIdentificationTrack.SDS &&
    part is StandardDeterminateSentence &&
    when (filter) {
      EarlyReleaseSentenceFilter.SDS_40_EXCLUSIONS -> part.hasAnSDSEarlyReleaseExclusion == SDSEarlyReleaseExclusionType.NO || part.hasAnSDSEarlyReleaseExclusion.isSDS40Tranche3Exclusion()
    }

  fun earliestTranche() = tranches.minOf { it.date }
}
