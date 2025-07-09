package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.EarlyReleaseConfiguration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.EarlyReleaseTrancheConfiguration
import java.time.LocalDate

data class TimelineCalculationDate(
  val date: LocalDate,
  val type: TimelineCalculationType,
  val earlyReleaseConfiguration: EarlyReleaseConfiguration? = null,
  val trancheConfiguration: EarlyReleaseTrancheConfiguration? = null,
)
