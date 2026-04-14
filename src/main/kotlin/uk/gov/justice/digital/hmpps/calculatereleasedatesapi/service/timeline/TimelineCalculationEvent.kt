package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.FTRLegislation.FTR56Legislation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.SDSLegislation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.TrancheConfiguration
import java.time.LocalDate

sealed interface TimelineCalculationEvent {
  val date: LocalDate
  val type: TimelineCalculationType

  data class SentenceTimelineCalculationEvent(override val date: LocalDate) : TimelineCalculationEvent {
    override val type: TimelineCalculationType = TimelineCalculationType.SENTENCED
  }

  data class AwardedAdjustmentTimelineCalculationEvent(override val date: LocalDate, override val type: TimelineCalculationType) : TimelineCalculationEvent

  data class UALTimelineCalculationEvent(override val date: LocalDate) : TimelineCalculationEvent {
    override val type: TimelineCalculationType = TimelineCalculationType.UAL
  }

  data class ExternalMovementTimelineCalculationEvent(override val date: LocalDate) : TimelineCalculationEvent {
    override val type: TimelineCalculationType = TimelineCalculationType.EXTERNAL_MOVEMENT
  }

  data class FTR56TrancheTimelineCalculationEvent(override val date: LocalDate, val legislation: FTR56Legislation, val tranche: TrancheConfiguration) : TimelineCalculationEvent {
    override val type: TimelineCalculationType = TimelineCalculationType.FTR56_TRANCHE
  }

  data class SDSLegislationAmendmentTimelineCalculationEvent(override val date: LocalDate, val legislation: SDSLegislation) : TimelineCalculationEvent {
    override val type: TimelineCalculationType = TimelineCalculationType.SDS_LEGISLATION_AMENDMENT
  }

  data class SDSLegislationCommencementTimelineCalculationEvent(override val date: LocalDate, val legislation: SDSLegislation) : TimelineCalculationEvent {
    override val type: TimelineCalculationType = TimelineCalculationType.SDS_LEGISLATION_COMMENCEMENT
  }

  data class SDSTrancheTimelineCalculationEvent(override val date: LocalDate, val legislation: SDSLegislation, val tranche: TrancheConfiguration) : TimelineCalculationEvent {
    override val type: TimelineCalculationType = TimelineCalculationType.EARLY_RELEASE_TRANCHE
  }
}
