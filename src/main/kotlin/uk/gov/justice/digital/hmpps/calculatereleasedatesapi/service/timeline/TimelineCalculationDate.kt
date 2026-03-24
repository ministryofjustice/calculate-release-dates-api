package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.TranchedLegislation
import java.time.LocalDate

data class TimelineCalculationDate(
  val date: LocalDate,
  val type: TimelineCalculationType,
  val tranchedLegislationToApplyOnDate: TranchedLegislation? = null,
)
