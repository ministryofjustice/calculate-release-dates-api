package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "feature-toggles")
data class FeatureToggles(
  var supportInactiveSentencesAndAdjustments: Boolean = false,
  var useAdjustmentsApi: Boolean = false,
  var concurrentConsecutiveSentencesEnabled: Boolean = false,
  var externalMovementsSds40: Boolean = false,
  var externalMovementsAdjustmentSharing: Boolean = false,
) {
  
  val externalMovementsEnabled: Boolean
    get() {
      return externalMovementsSds40 || externalMovementsAdjustmentSharing
    }
}

