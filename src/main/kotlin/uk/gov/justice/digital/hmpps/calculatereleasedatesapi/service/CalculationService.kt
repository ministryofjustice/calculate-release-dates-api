package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.FeatureToggles
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationRule
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationOptions
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationOutput
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationUserInputs
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.OperativeSentenceEnvelope
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.OperativeSentenceEnvelopeSource
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.PreviouslyRecordedSLED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ReleaseDateCalculationBreakdown
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.StandardDeterminateSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.sentence.SentenceIdentificationService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.BookingTimelineService
import java.time.temporal.ChronoUnit

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
    calculateSentenceLevelDates: Boolean,
  ): CalculationOutput {
    val options = CalculationOptions(calculationUserInputs.calculateErsed)
    // identify the types of the sentences
    for (sentence in booking.sentences) {
      sentenceIdentificationService.identify(sentence, booking.offender)
    }

    var calculatedReleaseDates = bookingTimelineService.calculate(booking.sentences, booking.adjustments, booking.offender, booking.returnToCustodyDate, options, booking.externalMovements)
    if (calculateSentenceLevelDates && featureToggles.storeSentenceLevelDates) {
      calculatedReleaseDates = calculatedReleaseDates.copy(sentenceLevelDates = sentenceLevelDatesService.extractSentenceLevelDates(calculatedReleaseDates))
    }

    val sledCalculatedForCurrentSentences = calculatedReleaseDates.calculationResult.dates[ReleaseDateType.SLED]
    val sledToOverrideTheCalculatedOneWith = if (calculationUserInputs.usePreviouslyRecordedSLEDIfFound && sledCalculatedForCurrentSentences != null) {
      previouslyRecordedSLEDService.findPreviouslyRecordedSLEDThatShouldOverrideTheCalculatedSLED(booking.offender.reference, sledCalculatedForCurrentSentences)
    } else {
      null
    }

    if (sledToOverrideTheCalculatedOneWith != null) {
      calculatedReleaseDates = usePreviouslyRecordedSLED(calculatedReleaseDates, sledToOverrideTheCalculatedOneWith)
    }

    if (featureToggles.storeOperativeSentenceEnvelope) {
      val earliestSentenceDate = calculatedReleaseDates.sentences.minOfOrNull { it.sentencedAt }
      val sledOrSed = calculatedReleaseDates.calculationResult.dates[ReleaseDateType.SLED] ?: calculatedReleaseDates.calculationResult.dates[ReleaseDateType.SED]
      if (earliestSentenceDate != null && sledOrSed != null) {
        calculatedReleaseDates = calculatedReleaseDates.copy(
          operativeSentenceEnvelope = OperativeSentenceEnvelope(
            sentenceEnvelopeLengthInDays = ChronoUnit.DAYS.between(earliestSentenceDate, sledOrSed) + 1, // start and end date should be inclusive
            earliestSentenceStartDate = earliestSentenceDate,
            isPostRecallSentenceEnvelope = calculatedReleaseDates.sentences.any { it.isRecall() },
            containsAnSDSPlusSentence = calculatedReleaseDates.sentences.any { it is StandardDeterminateSentence && it.releaseArrangements.isSDSPlus },
            sentenceEnvelopeSource = OperativeSentenceEnvelopeSource.CRDS,
          ),
        )
      }
    }

    return calculatedReleaseDates
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
