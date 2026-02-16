package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.FeatureToggles
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationRule
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationOptions
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationOutput
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationUserInputs
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.PreviouslyRecordedSLED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ReleaseDateCalculationBreakdown
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.sentence.SentenceIdentificationService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.BookingTimelineService

@Service
class CalculationService(
  val sentenceIdentificationService: SentenceIdentificationService,
  private val bookingTimelineService: BookingTimelineService,
  private val featureToggles: FeatureToggles,
  private val previouslyRecordedSLEDService: PreviouslyRecordedSLEDService,
  private val sentenceLevelDatesService: SentenceLevelDatesService,
) {

  fun calculateReleaseDates(
    booking: Booking,
    calculationUserInputs: CalculationUserInputs,
  ): CalculationOutput {
    val options = CalculationOptions(calculationUserInputs.calculateErsed)
    // identify the types of the sentences
    for (sentence in booking.sentences) {
      sentenceIdentificationService.identify(sentence, booking.offender)
    }

    var calculatedReleaseDates = bookingTimelineService.calculate(booking.sentences, booking.adjustments, booking.offender, booking.returnToCustodyDate, options, booking.externalMovements)
    if (featureToggles.storeSentenceLevelDates) {
      calculatedReleaseDates = calculatedReleaseDates.copy(sentenceLevelDates = sentenceLevelDatesService.extractSentenceLevelDates(calculatedReleaseDates))
    }
    val sledCalculatedForCurrentSentences = calculatedReleaseDates.calculationResult.dates[ReleaseDateType.SLED]
    val sledToOverrideTheCalculatedOneWith = if (calculationUserInputs.usePreviouslyRecordedSLEDIfFound && sledCalculatedForCurrentSentences != null) {
      previouslyRecordedSLEDService.findPreviouslyRecordedSLEDThatShouldOverrideTheCalculatedSLED(booking.offender.reference, sledCalculatedForCurrentSentences)
    } else {
      null
    }

    return if (sledToOverrideTheCalculatedOneWith != null) {
      usePreviouslyRecordedSLED(calculatedReleaseDates, sledToOverrideTheCalculatedOneWith)
    } else {
      calculatedReleaseDates
    }
  }

  private fun usePreviouslyRecordedSLED(
    calculationOutput: CalculationOutput,
    sledToOverrideTheCalculatedOneWith: PreviouslyRecordedSLED,
  ): CalculationOutput {
    val newDates = calculationOutput.calculationResult.dates.toMutableMap()
    val newBreakdowns = calculationOutput.calculationResult.breakdownByReleaseDateType.toMutableMap()
    newDates[ReleaseDateType.SLED] = sledToOverrideTheCalculatedOneWith.previouslyRecordedSLEDDate
    newBreakdowns[ReleaseDateType.SLED] = ReleaseDateCalculationBreakdown(
      releaseDate = sledToOverrideTheCalculatedOneWith.previouslyRecordedSLEDDate,
      unadjustedDate = sledToOverrideTheCalculatedOneWith.calculatedDate,
      rules = setOf(CalculationRule.PREVIOUSLY_RECORDED_SLED_USED),
    )
    if (newDates.containsKey(ReleaseDateType.TUSED) && sledToOverrideTheCalculatedOneWith.previouslyRecordedSLEDDate.isAfter(newDates[ReleaseDateType.TUSED])) {
      newDates.remove(ReleaseDateType.TUSED)
      newBreakdowns.remove(ReleaseDateType.TUSED)
    }

    return calculationOutput.copy(
      calculationResult = calculationOutput.calculationResult.copy(
        dates = newDates,
        breakdownByReleaseDateType = newBreakdowns,
        usedPreviouslyRecordedSLED = sledToOverrideTheCalculatedOneWith,
      ),
    )
  }
}
