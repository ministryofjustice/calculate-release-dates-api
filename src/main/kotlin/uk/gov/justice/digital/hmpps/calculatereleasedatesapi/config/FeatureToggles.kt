package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "feature-toggles")
data class FeatureToggles(
  var supportInactiveSentencesAndAdjustments: Boolean = false,
  var externalMovementsEnabled: Boolean = false,
  var useAdjustmentsApi: Boolean = false,
  var concurrentConsecutiveSentencesEnabled: Boolean = false,
)
