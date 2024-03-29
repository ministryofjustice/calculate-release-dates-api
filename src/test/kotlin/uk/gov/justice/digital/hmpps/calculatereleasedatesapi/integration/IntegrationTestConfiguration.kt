package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.info.BuildProperties
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Properties

@TestConfiguration
class IntegrationTestConfiguration {

  /**
   * Normally generated by gradle using springboot's build info goal but it's not compatible with build and run using intellij which this fixes.
   * The proper version from Gradle is used during CI. See META-INF/build-info.properties for properties.
   */
  @Bean
  @ConditionalOnMissingBean(BuildProperties::class)
  fun buildProperties(): BuildProperties {
    val properties = Properties()
    properties.setProperty("artifact", "calculate-release-dates-api")
    properties.setProperty("by", "foo.bar")
    properties.setProperty("group", "uk.gov.justice.digital.hmpps")
    properties.setProperty("machine", "MJ000000")
    properties.setProperty("name", "calculate-release-dates-api")
    properties.setProperty("operatingSystem", "Mac OS X (14.3.1)")
    properties.setProperty("time", LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME))
    properties.setProperty("version", LocalDate.now().format(DateTimeFormatter.ISO_DATE))
    return BuildProperties(properties)
  }
}
