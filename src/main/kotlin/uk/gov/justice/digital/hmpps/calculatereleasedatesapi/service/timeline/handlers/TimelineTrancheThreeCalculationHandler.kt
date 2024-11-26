package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.handlers

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.SDS40TrancheConfiguration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceIdentificationTrack
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AbstractSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculableSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.ReleasePointMultiplierLookup
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.t3offenceCodes
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineCalculator
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineHandleResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineTrackingData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.isAfterOrEqualTo
import java.time.LocalDate

@Service
class TimelineTrancheThreeCalculationHandler(
  trancheConfiguration: SDS40TrancheConfiguration,
  multiplierLookup: ReleasePointMultiplierLookup,
  timelineCalculator: TimelineCalculator,
) : TimelineCalculationHandler(trancheConfiguration, multiplierLookup, timelineCalculator) {

  override fun handle(
    timelineCalculationDate: LocalDate,
    timelineTrackingData: TimelineTrackingData,
  ): TimelineHandleResult {
    val updateOffenceTrack: (List<AbstractSentence>) -> Unit = { sentences ->
      sentences
        .filter { it.offence.offenceCode in t3offenceCodes && it.identificationTrack === SentenceIdentificationTrack.SDS_EARLY_RELEASE }
        .forEach { sentencePart ->
          sentencePart.identificationTrack = SentenceIdentificationTrack.SDS_STANDARD_RELEASE_T3_EXCLUSION
        }
    }

    val updateToStandardReleaseIfViolation: (calculableSentence: CalculableSentence) -> Unit = { calculableSentence ->
      updateOffenceTrack(calculableSentence.sentenceParts())
      calculableSentence.sentenceCalculation.unadjustedReleaseDate.findMultiplierByIdentificationTrack =
        multiplierFnForDate(timelineCalculationDate, timelineTrackingData.trancheAndCommencement.second)
    }

    with(timelineTrackingData) {
      custodialSentences
        .filter { calculableSentence -> calculableSentence.sentenceParts().any { it.offence.offenceCode in t3offenceCodes } }
        .forEach { calculableSentence -> updateToStandardReleaseIfViolation(calculableSentence) }

      licenseSentences
        .filter { calculableSentence -> calculableSentence.sentenceParts().any { it.offence.offenceCode in t3offenceCodes } }
        .filter { it.sentenceCalculation.adjustedDeterminateReleaseDate.isAfterOrEqualTo(trancheConfiguration.trancheThreeCommencementDate) }
        .forEach { calculableSentence -> updateToStandardReleaseIfViolation(calculableSentence) }
    }

    return TimelineHandleResult()
  }
}
