package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.Name
import org.springframework.format.annotation.DateTimeFormat
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.EarlyReleaseConfiguration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.EarlyReleaseSentenceFilter
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.EarlyReleaseTrancheConfiguration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.EarlyReleaseTrancheType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SDSEarlyReleaseTranche
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceIdentificationTrack
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.ReleaseMultiplier
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@ConfigurationProperties("sds-40-early-release-tranches")
data class SDS40TrancheConfiguration(
  @Name("tranche-one-date")
  @DateTimeFormat(pattern = "yyyy-MM-dd") val trancheOneCommencementDate: LocalDate,
  @Name("tranche-two-date")
  @DateTimeFormat(pattern = "yyyy-MM-dd") val trancheTwoCommencementDate: LocalDate,
  @Name("tranche-three-date")
  @DateTimeFormat(pattern = "yyyy-MM-dd") val trancheThreeCommencementDate: LocalDate,
) {
  fun getSds40EarlyReleaseConfig(releaseMultiplier: ReleaseMultiplier) = EarlyReleaseConfiguration(
    releaseMultiplier = mapOf(SentenceIdentificationTrack.SDS to releaseMultiplier),
    filter = EarlyReleaseSentenceFilter.SDS_40_EXCLUSIONS,
    tranches = listOf(
      EarlyReleaseTrancheConfiguration(
        type = EarlyReleaseTrancheType.SENTENCE_LENGTH,
        date = trancheOneCommencementDate,
        duration = 5,
        unit = ChronoUnit.YEARS,
        name = SDSEarlyReleaseTranche.TRANCHE_1,
      ),
      EarlyReleaseTrancheConfiguration(
        type = EarlyReleaseTrancheType.FINAL,
        date = trancheTwoCommencementDate,
        name = SDSEarlyReleaseTranche.TRANCHE_2,
      ),
      EarlyReleaseTrancheConfiguration(
        type = EarlyReleaseTrancheType.SDS_40_TRANCHE_3,
        date = trancheThreeCommencementDate,
      ),
    ),
  )
}
