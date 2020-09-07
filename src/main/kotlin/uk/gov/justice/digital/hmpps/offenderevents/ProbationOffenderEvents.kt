package uk.gov.justice.digital.hmpps.offenderevents

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication()
class ProbationOffenderEvents

fun main(args: Array<String>) {
  runApplication<ProbationOffenderEvents>(*args)
}