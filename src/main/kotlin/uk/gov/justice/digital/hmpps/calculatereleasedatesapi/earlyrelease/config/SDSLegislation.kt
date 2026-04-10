package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceIdentificationTrack
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AbstractSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculableSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ExternalMovementReason
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.StandardDeterminateSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.ReleaseMultiplier
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.ExternalMovementTimeline
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineCalculationDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineCalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineTrackingData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.isAfterOrEqualTo
import java.time.LocalDate

sealed interface SDSLegislation : Legislation {
  val releaseMultiplier: Map<SentenceIdentificationTrack, ReleaseMultiplier>
  val filter: EarlyReleaseSentenceFilter

  fun requiredTimelineCalculations(): List<TimelineCalculationDate> = listOf(TimelineCalculationDate(commencementDate(), TimelineCalculationType.SDS_LEGISLATION_COMMENCEMENT, this))

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

    override fun requiredTimelineCalculations(): List<TimelineCalculationDate> = super.requiredTimelineCalculations() + tranches.map {
      TimelineCalculationDate(it.date, TimelineCalculationType.EARLY_RELEASE_TRANCHE, this)
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

    override fun requiredTimelineCalculations(): List<TimelineCalculationDate> = super.requiredTimelineCalculations() + TimelineCalculationDate(commencementDate, TimelineCalculationType.SDS_LEGISLATION_AMENDMENT, this)
  }

  data class ProgressionModelLegislation(
    override val tranches: List<TrancheConfiguration>,
    override val releaseMultiplier: Map<SentenceIdentificationTrack, ReleaseMultiplier>,
    override val filter: EarlyReleaseSentenceFilter,
  ) : SDSLegislationWithTranches {
    override val legislationName = LegislationName.SDS_PROGRESSION_MODEL
    override val trancheSelectionStrategy: TrancheSelectionStrategy = SDSTrancheSelectionStrategy

    override fun requiredTimelineCalculations(): List<TimelineCalculationDate> = super.requiredTimelineCalculations() + tranches.map {
      TimelineCalculationDate(it.date, TimelineCalculationType.EARLY_RELEASE_TRANCHE, this)
    }

    override fun commencementDate(): LocalDate = tranches.minOf { it.date }

    override fun isSentenceSubjectToTraches(sentence: CalculableSentence) = sentence is StandardDeterminateSentence && this.releaseMultiplier.keys.contains(sentence.identificationTrack) && !sentence.section250
  }

  private object SDSTrancheSelectionStrategy : TrancheSelectionStrategy {
    override fun hasSentencesThatMightApplyToTheTranche(
      timelineTrackingData: TimelineTrackingData,
      legislation: Legislation,
    ): Boolean {
      require(legislation is SDSLegislationWithTranches) { "Tried to allocate non SDSLegislation using SDS tranche allocation strategy" }
      val sentencesWithReleaseAfterTrancheCommencement = (timelineTrackingData.currentSentenceGroup + timelineTrackingData.licenceSentences).filter {
        it.sentenceCalculation.adjustedDeterminateReleaseDate.isAfter(legislation.commencementDate())
      }
      return sentencesWithReleaseAfterTrancheCommencement.any { sentence ->
        sentence.sentenceParts().any { sentencePart ->
          val isInRangeOfEarlyRelease = sentencePart.sentencedAt.isBefore(legislation.commencementDate())
          isInRangeOfEarlyRelease && legislation.isSentenceSubjectToTraches(sentencePart)
        }
      }
    }

    override fun sentencesToMatchOnSentenceLength(
      timelineTrackingData: TimelineTrackingData,
      legislation: Legislation,
    ): List<CalculableSentence> = (timelineTrackingData.licenceSentences + timelineTrackingData.currentSentenceGroup)
      .filter { filterOnSentenceExpiryDates(it, legislation) }

    private fun filterOnSentenceExpiryDates(sentence: CalculableSentence, legislation: Legislation): Boolean = sentence.sentenceCalculation.adjustedExpiryDate.isAfterOrEqualTo(legislation.commencementDate())
  }
}
