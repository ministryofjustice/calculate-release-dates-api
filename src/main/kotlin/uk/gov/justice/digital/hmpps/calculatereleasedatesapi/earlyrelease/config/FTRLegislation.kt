package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineCalculationDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineCalculationType
import java.time.LocalDate

sealed interface FTRLegislation : Legislation {
  fun isFTR56Supported(): Boolean = false

  fun requiredTimelineCalculations(): List<TimelineCalculationDate>

  data class FTR56Legislation(override val tranches: List<TrancheConfiguration>) :
    FTRLegislation,
    LegislationWithTranches {
    override val legislationName = LegislationName.FTR_56
    override val trancheSelectionStrategy: TrancheSelectionStrategy = FTR56TrancheSelectionStrategy

    override fun isFTR56Supported(): Boolean = true

    override fun requiredTimelineCalculations() = tranches.map { TimelineCalculationDate(it.date, TimelineCalculationType.FTR56_TRANCHE) }

    override fun commencementDate(): LocalDate = tranches.minOf { it.date }
  }
}
