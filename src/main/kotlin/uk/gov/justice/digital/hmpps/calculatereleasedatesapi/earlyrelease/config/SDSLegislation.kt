package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.SDSTrancheSelectionStrategy.SDS40TrancheSelectionStrategy
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.SDSTrancheSelectionStrategy.SDSProgressionModelTrancheSelectionStrategy
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceIdentificationTrack
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AbstractSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculableSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ExternalMovementReason
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.StandardDeterminateSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.ImportantDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.ReleaseMultiplier
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.ExternalMovementTimeline
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineCalculationEvent
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineCalculationEvent.SDSLegislationAmendmentTimelineCalculationEvent
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineCalculationEvent.SDSLegislationCommencementTimelineCalculationEvent
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineCalculationEvent.SDSTrancheTimelineCalculationEvent
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineTrackingData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.isAfterOrEqualTo
import java.time.LocalDate
import java.time.temporal.ChronoUnit

sealed interface SDSLegislation : Legislation {
  val releaseMultiplier: Map<SentenceIdentificationTrack, ReleaseMultiplier>
  val filter: EarlyReleaseSentenceFilter

  fun requiredTimelineCalculations(): List<TimelineCalculationEvent> = listOf(SDSLegislationCommencementTimelineCalculationEvent(commencementDate(), legislation = this))

  fun appliesToSentence(part: AbstractSentence) = filter.isIncluded(part)

  fun applyDefaulting(calculatedDate: LocalDate, earliestApplicableDate: LocalDate?, awardedDays: Long): DefaultingResult = if (earliestApplicableDate != null && earliestApplicableDate.isAfter(calculatedDate)) {
    DefaultingResult(earliestApplicableDate, DefaultingOutcome.DEFAULTED)
  } else {
    DefaultingResult(calculatedDate, DefaultingOutcome.RETAINED)
  }

  fun modifyConditionalReleaseAndReleaseDateTypes(sentence: CalculableSentence) = Unit

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
    override val trancheSelectionStrategy: SDSTrancheSelectionStrategy = SDS40TrancheSelectionStrategy()

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
  ) : SDSLegislationWithTranches {
    override val filter: EarlyReleaseSentenceFilter = EarlyReleaseSentenceFilter.SDS_PROGRESSION_MODEL
    override val legislationName = LegislationName.SDS_PROGRESSION_MODEL
    override val trancheSelectionStrategy: SDSTrancheSelectionStrategy = SDSProgressionModelTrancheSelectionStrategy()

    override fun requiredTimelineCalculations(): List<TimelineCalculationEvent> = super.requiredTimelineCalculations() + tranches.map { tranche ->
      SDSTrancheTimelineCalculationEvent(tranche.date, legislation = this, tranche)
    }

    override fun commencementDate(): LocalDate = tranches.minOf { it.date }

    override fun isSentenceSubjectToTraches(sentence: CalculableSentence) = sentence is StandardDeterminateSentence && filter.isIncluded(sentence)

    /*
     * ADAs should always be included in the final release dates. We remove them when comparing to the standard release date as if they would be due for
     * release without them then they are not eligible for early release.
     * We also remove ADAs when deciding whether to default to the tranche commencement date for the same reason and in the case they are defaulted to tranche
     * commencement the ADAs are then added onto it.
     */
    override fun applyDefaulting(calculatedDate: LocalDate, earliestApplicableDate: LocalDate?, awardedDays: Long): DefaultingResult {
      val calculatedDateMinusAwardedAndUalPostProgression = calculatedDate.minusDays(awardedDays)
      return if (earliestApplicableDate != null && earliestApplicableDate.isAfter(calculatedDateMinusAwardedAndUalPostProgression)) {
        DefaultingResult(earliestApplicableDate.plusDays(awardedDays), DefaultingOutcome.DEFAULTED)
      } else {
        DefaultingResult(calculatedDate, DefaultingOutcome.RETAINED)
      }
    }

    /*
     * Under progression model rules sentences of less than 12 months for offences committed before ORA are no longer
     * eligible for unconditional release and should have CRD + SLED instead of ARD + SED.
     */
    override fun modifyConditionalReleaseAndReleaseDateTypes(sentence: CalculableSentence) {
      if (sentence.durationIsLessThan(12, ChronoUnit.MONTHS) && sentence.offence.committedAt?.isBefore(ImportantDates.ORA_DATE) == true) {
        sentence.sentenceCalculation.isReleaseDateConditional = true
        val switchedArdAndSedForCrdAndSled = sentence.releaseDateTypes.initialTypes.toMutableList()
        switchedArdAndSedForCrdAndSled += ReleaseDateType.SLED
        switchedArdAndSedForCrdAndSled += ReleaseDateType.CRD
        switchedArdAndSedForCrdAndSled -= ReleaseDateType.SED
        switchedArdAndSedForCrdAndSled -= ReleaseDateType.ARD
        sentence.releaseDateTypes = sentence.releaseDateTypes.copy(initialTypes = switchedArdAndSedForCrdAndSled)
      }
    }
  }
}
