package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config

import org.springframework.boot.context.properties.ConfigurationProperties
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ConfigItem

@ConfigurationProperties(prefix = "feature-toggles")
data class FeatureToggles(
  var supportInactiveSentencesAndAdjustments: Boolean = false,
  var useAdjustmentsApi: Boolean = false,
  var applyPostRecallRepealRules: Boolean = false,
  var storeSentenceLevelDates: Boolean = false,
  var storeOperativeSentenceEnvelope: Boolean = false,
  var adultHdcSuspended: Boolean = false,
) {
  fun toConfigItems(): List<ConfigItem> = listOf(
    ConfigItem("Support inactive sentences and adjustments", supportInactiveSentencesAndAdjustments.toString()),
    ConfigItem("Use adjustments API", useAdjustmentsApi.toString()),
    ConfigItem("Apply post recall repeal rules (disable TUSED)", applyPostRecallRepealRules.toString()),
    ConfigItem("Store sentence level dates", storeSentenceLevelDates.toString()),
    ConfigItem("Store operative sentence envelope for probation API", storeOperativeSentenceEnvelope.toString()),
    ConfigItem("Adult HDC suspended (clean stop)", adultHdcSuspended.toString()),
  )
}
