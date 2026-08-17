package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config

import org.springframework.boot.context.properties.ConfigurationProperties
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ConfigItem

@ConfigurationProperties(prefix = "feature-toggles")
data class FeatureToggles(
  var supportInactiveSentencesAndAdjustments: Boolean = false,
  var useAdjustmentsApi: Boolean = false,
  var applyPostRecallRepealRules: Boolean = false,
  var recordARecallFtr56Rules: Boolean = false,
  var storeSentenceLevelDates: Boolean = false,
  var storeOperativeSentenceEnvelope: Boolean = false,
  var applyPostHdcedRepealRules: Boolean = false,
  var adultHdcSuspended: Boolean = false,
  var secondCheckEnabled: Boolean = false,
  var routePreProgressionExtinguishedSentenceToManual: Boolean = false,
  var progressionTrancheOneManualJourney: Boolean = false,
  var progressionModelScheduleExclusionEnabled: Boolean = false,
  var routeProgressionModelScheduleExclusionToManual: Boolean = false,
  var useLatestErsedFromPreErs30Snapshot: Boolean = false,
) {
  fun toConfigItems(): List<ConfigItem> = listOf(
    ConfigItem("Support inactive sentences and adjustments", supportInactiveSentencesAndAdjustments.toString()),
    ConfigItem("Use adjustments API", useAdjustmentsApi.toString()),
    ConfigItem("Apply post recall repeal rules (disable TUSED)", applyPostRecallRepealRules.toString()),
    ConfigItem("Record A Recall FTR56 Rules", recordARecallFtr56Rules.toString()),
    ConfigItem("Store sentence level dates", storeSentenceLevelDates.toString()),
    ConfigItem("Store operative sentence envelope for probation API", storeOperativeSentenceEnvelope.toString()),
    ConfigItem("Apply post HDCED repeal rules (Adult HDC pre-PM still allowed)", applyPostHdcedRepealRules.toString()),
    ConfigItem("Adult HDC suspended (clean stop)", adultHdcSuspended.toString()),
    ConfigItem("Second check enabled", secondCheckEnabled.toString()),
    ConfigItem("Route pre-progression extinguished sentence to manual (post-PM is too much remand validation error)", routePreProgressionExtinguishedSentenceToManual.toString()),
    ConfigItem("Progression Model route T1 to manual", progressionTrancheOneManualJourney.toString()),
    ConfigItem("Progression Model excluded offences enabled", progressionModelScheduleExclusionEnabled.toString()),
    ConfigItem("Progression Model route excluded offences to manual", routeProgressionModelScheduleExclusionToManual.toString()),
    ConfigItem("Use latest ERSED from pre-ERS30 snapshot (CRS-2812)", useLatestErsedFromPreErs30Snapshot.toString()),
  )
}
