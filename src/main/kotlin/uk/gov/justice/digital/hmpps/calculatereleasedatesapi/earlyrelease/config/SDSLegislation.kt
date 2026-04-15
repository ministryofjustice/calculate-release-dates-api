package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceIdentificationTrack
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AbstractSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculableSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ExternalMovementReason
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.StandardDeterminateSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.ReleaseMultiplier
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.ExternalMovementTimeline
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineCalculationEvent
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineCalculationEvent.SDSLegislationAmendmentTimelineCalculationEvent
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineCalculationEvent.SDSLegislationCommencementTimelineCalculationEvent
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineCalculationEvent.SDSTrancheTimelineCalculationEvent
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineTrackingData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.isAfterOrEqualTo
import java.time.LocalDate

sealed interface SDSLegislation : Legislation {
  val releaseMultiplier: Map<SentenceIdentificationTrack, ReleaseMultiplier>
  val filter: EarlyReleaseSentenceFilter

  fun requiredTimelineCalculations(): List<TimelineCalculationEvent> = listOf(SDSLegislationCommencementTimelineCalculationEvent(commencementDate(), legislation = this))

  fun appliesToSentence(part: AbstractSentence) = filter.matches(part)

  data class DefaultSDSLegislation(
    override val releaseMultiplier: Map<SentenceIdentificationTrack, ReleaseMultiplier>,
    override val filter: EarlyReleaseSentenceFilter,
  ) : SDSLegislation {
    override val legislationName = LegislationName.SDS_DEFAULT

    override fun commencementDate(): LocalDate = LocalDate.MIN
  }

  data class SDS40Legislation(
    override val tranches: List<TrancheConfiguration>,
    override val releaseMultiplier: Map<SentenceIdentificationTrack, ReleaseMultiplier>,
    override val filter: EarlyReleaseSentenceFilter,
  ) : SDSLegislationWithTranches {
    override val legislationName = LegislationName.SDS_40
    override val trancheSelectionStrategy: TrancheSelectionStrategy = SDSTrancheSelectionStrategy

    override fun requiredTimelineCalculations(): List<TimelineCalculationEvent> = super.requiredTimelineCalculations() + tranches.map { tranche ->
      SDSTrancheTimelineCalculationEvent(tranche.date, legislation = this, tranche)
    }

    override fun commencementDate(): LocalDate = tranches.minOf { it.date }

    override fun anyReasonTheTrancheCannotApply(
      allocatedTranche: TrancheConfiguration,
      timelineTrackingData: TimelineTrackingData,
    ): Boolean = isOutOfCustodyAtAllocatedTrancheDate(allocatedTranche.date, timelineTrackingData.externalMovements, timelineTrackingData.previousUalPeriods)

    private fun isOutOfCustodyAtAllocatedTrancheDate(allocatedTrancheDate: LocalDate, externalMovements: ExternalMovementTimeline, previousUalPeriods: MutableList<Pair<LocalDate, LocalDate>>): Boolean {
      // If this is a recall or historical calculation for a booking where they defaulted to the tranche date then there will be a CRD release on the tranche date
      val outOfCustodyStatus = externalMovements.statusBeforeDate(allocatedTrancheDate)
      if (outOfCustodyStatus != null) {
        // They are out of prison. The following code checking for any exemptions to that.

        // If they were a HDC, ERS or ECSL release then they should not be early released.
        if (listOf(ExternalMovementReason.HDC, ExternalMovementReason.ERS, ExternalMovementReason.ECSL).contains(outOfCustodyStatus.release.movementReason)) {
          return true
        }

        // If the person was UAL at tranche commencement then they are subject to early release.
        return previousUalPeriods.none {
          it.first.isBefore(allocatedTrancheDate) && it.second.isAfterOrEqualTo(allocatedTrancheDate)
        }
      }
      return false
    }

    override fun isSentenceSubjectToTraches(sentence: CalculableSentence) = this.releaseMultiplier.keys.contains(sentence.identificationTrack)
  }

  data class SDS40AdditionalExcludedOffencesLegislation(
    val commencementDate: LocalDate,
    override val releaseMultiplier: Map<SentenceIdentificationTrack, ReleaseMultiplier>,
    override val filter: EarlyReleaseSentenceFilter,
  ) : SDSLegislation {
    override val legislationName = LegislationName.SDS_40_ADDITIONAL_EXCLUDED_OFFENCES

    override fun commencementDate(): LocalDate = commencementDate

    override fun requiredTimelineCalculations(): List<TimelineCalculationEvent> = super.requiredTimelineCalculations() + SDSLegislationAmendmentTimelineCalculationEvent(commencementDate, legislation = this)
  }

  data class ProgressionModelLegislation(
    override val tranches: List<TrancheConfiguration>,
    override val releaseMultiplier: Map<SentenceIdentificationTrack, ReleaseMultiplier>,
    override val filter: EarlyReleaseSentenceFilter,
  ) : SDSLegislationWithTranches {
    override val legislationName = LegislationName.SDS_PROGRESSION_MODEL
    override val trancheSelectionStrategy: TrancheSelectionStrategy = SDSTrancheSelectionStrategy

    override fun requiredTimelineCalculations(): List<TimelineCalculationEvent> = super.requiredTimelineCalculations() + tranches.map { tranche ->
      SDSTrancheTimelineCalculationEvent(tranche.date, legislation = this, tranche)
    }

    override fun commencementDate(): LocalDate = tranches.minOf { it.date }

    override fun isSentenceSubjectToTraches(sentence: CalculableSentence) = sentence is StandardDeterminateSentence && this.releaseMultiplier.keys.contains(sentence.identificationTrack) && !sentence.section250
  }
}
