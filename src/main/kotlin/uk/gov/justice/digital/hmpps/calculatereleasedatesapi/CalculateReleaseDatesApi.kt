package uk.gov.justice.digital.hmpps.calculatereleasedatesapi

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication()
@ConfigurationPropertiesScan
@EnableScheduling
class CalculateReleaseDatesApi

fun main(args: Array<String>) {
  runApplication<CalculateReleaseDatesApi>(args = args)
}
