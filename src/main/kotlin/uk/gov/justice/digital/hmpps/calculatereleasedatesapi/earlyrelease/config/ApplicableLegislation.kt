package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config

import java.time.LocalDate

data class ApplicableLegislation<T>(
  val legislation: T,
  val earliestApplicableDate: LocalDate? = null,
)
