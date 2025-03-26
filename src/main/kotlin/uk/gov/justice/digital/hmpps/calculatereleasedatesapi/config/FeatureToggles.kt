package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "feature-toggles")
data class FeatureToggles(
  var sdsEarlyRelease: Boolean = false,
  var sdsEarlyReleaseHints: Boolean = false,
  var supportInactiveSentencesAndAdjustments: Boolean = false,
  var toreraOffenceToManualJourney: Boolean = false,
  var botusConsecutiveJourney: Boolean = false,
  var botusConcurrentJourney: Boolean = false,
  var hdc365: Boolean = false,
  var externalMovementsEnabled: Boolean = false,
  var revisedFixedTermRecallsRules: Boolean = false,
  var concurrentConsecutiveSentencesEnabled: Boolean = false,
)
