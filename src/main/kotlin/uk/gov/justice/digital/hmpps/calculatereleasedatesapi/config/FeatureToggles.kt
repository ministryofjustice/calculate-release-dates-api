package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "feature-toggles")
data class FeatureToggles(
  var botus: Boolean = false,
  var sdsEarlyRelease: Boolean = false,
  var sdsEarlyReleaseUnsupported: Boolean = false,
)
