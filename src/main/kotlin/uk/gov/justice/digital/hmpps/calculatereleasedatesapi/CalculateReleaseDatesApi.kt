package uk.gov.justice.digital.hmpps.calculatereleasedatesapi

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication()
class CalculateReleaseDatesApi

fun main(args: Array<String>) {
  runApplication<CalculateReleaseDatesApi>(*args)
}
