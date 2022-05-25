package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.LocalDate

@ConfigurationProperties(prefix = "feature-toggles")
data class FeatureToggles(
  var eds: Boolean = false,
  private var pcscStartDateString: String = "2022-06-27"
) {
  val pcscStartDate = LocalDate.parse(pcscStartDateString)
}
