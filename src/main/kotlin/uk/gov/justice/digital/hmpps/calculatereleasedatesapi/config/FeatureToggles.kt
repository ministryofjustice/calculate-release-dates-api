package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "feature-toggles")
data class FeatureToggles(
  var botus: Boolean = false,
  var sdsEarlyRelease: Boolean = false,
  var sdsEarlyReleaseHints: Boolean = false,
  var supportInactiveSentencesAndAdjustments: Boolean = false,
  var toreraOffenceToManualJourney: Boolean = false,
)
