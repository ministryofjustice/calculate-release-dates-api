package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.LocalDate

@ConfigurationProperties(prefix = "feature-toggles")
data class FeatureToggles(
  var eds: Boolean = false,
  /* This will allow us to change the comencement date of PCSC in order to test the functionality. */
  //TODO there must be a better way to inject a date from property.
  var pcscStartDateString: String = "2022-06-28"
) {
  val pcscStartDate: LocalDate get() {
    return LocalDate.parse(pcscStartDateString)
  }
}
