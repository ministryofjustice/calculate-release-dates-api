package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "feature-toggles")
data class FeatureToggles(
  var supportInactiveSentencesAndAdjustments: Boolean = false,
  var useAdjustmentsApi: Boolean = false,
  var applyPostRecallRepealRules: Boolean = false,
  var recordARecallFtr56Rules: Boolean = false,
  var storeSentenceLevelDates: Boolean = false,
)
