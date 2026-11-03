package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config

import org.springframework.boot.context.properties.ConfigurationProperties
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ConfigItem

@ConfigurationProperties(prefix = "feature-toggles")
data class FeatureToggles(
  var adultHdcSuspended: Boolean = false,
  var ersZeroEnabled: Boolean = false,
  var moveAndMergeEventsEnabled: Boolean = false,
) {
  fun toConfigItems(): List<ConfigItem> = listOf(
    ConfigItem("Adult HDC suspended (clean stop)", adultHdcSuspended.toString()),
    ConfigItem("ERS Zero Enabled", ersZeroEnabled.toString()),
    ConfigItem("Prisoner merge & booking move events enabled", moveAndMergeEventsEnabled.toString()),
  )
}
