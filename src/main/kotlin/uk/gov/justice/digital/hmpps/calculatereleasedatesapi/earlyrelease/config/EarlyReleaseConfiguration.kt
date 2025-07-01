package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "early-release-configuration")
data class EarlyReleaseConfiguration(
 val releaseMultiplier: Double,
  val tranches: List<EarlyReleaseTrancheConfiguration>
)