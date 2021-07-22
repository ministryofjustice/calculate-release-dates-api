package uk.gov.justice.digital.hmpps.calculatereleasedatesapi

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication()
@ConfigurationPropertiesScan
class CalculateReleaseDatesApi

fun main(args: Array<String>) {
  runApplication<CalculateReleaseDatesApi>(args = args)
}
