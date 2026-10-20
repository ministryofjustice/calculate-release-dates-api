package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config

import org.springframework.boot.context.properties.ConfigurationProperties
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ConfigItem

@ConfigurationProperties(prefix = "feature-toggles")
data class FeatureToggles(
  var applyPostRecallRepealRules: Boolean = false,
  var adultHdcSuspended: Boolean = false,
) {
  fun toConfigItems(): List<ConfigItem> = listOf(
    ConfigItem("Apply post recall repeal rules (disable TUSED)", applyPostRecallRepealRules.toString()),
    ConfigItem("Adult HDC suspended (clean stop)", adultHdcSuspended.toString()),
  )
}
