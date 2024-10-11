package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationUserInputs
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonApiSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAndOffence

@Service
class ValidationService(
  private val preCalculationValidationService: PreCalculationValidationService,
  private val adjustmentValidationService: AdjustmentValidationService,
  private val recallValidationService: RecallValidationService,
  private val sentenceValidationService: SentenceValidationService,
  private val validationUtilities: ValidationUtilities,
  private val postCalculationValidationService: PostCalculationValidationService,
) {

  fun validateBeforeCalculation(
    sourceData: PrisonApiSourceData,
    calculationUserInputs: CalculationUserInputs,
  ): List<ValidationMessage> {
    log.info("Pre-calculation validation of source data")
    val sentencesAndOffences = sourceData.sentenceAndOffences
    val adjustments = sourceData.bookingAndSentenceAdjustments
    val sortedSentences = sentencesAndOffences.sortedWith(validationUtilities::sortByCaseNumberAndLineSequence)

    val validateOffender = preCalculationValidationService.validateOffenderSupported(sourceData.prisonerDetails)
    if (validateOffender.isNotEmpty()) {
      return validateOffender
    }

    val unsupportedValidationMessages = preCalculationValidationService.validateSupportedSentences(sortedSentences)
    if (unsupportedValidationMessages.isNotEmpty()) {
      return unsupportedValidationMessages
    }

    val unsupportedCalculationMessages = preCalculationValidationService.validateUnsupportedCalculation(sourceData)
    if (unsupportedCalculationMessages.isNotEmpty()) {
      return unsupportedCalculationMessages
    }

    val unsupportedOffenceMessages = preCalculationValidationService.validateUnsupportedOffences(sentencesAndOffences)
    if (unsupportedOffenceMessages.isNotEmpty()) {
      return unsupportedOffenceMessages
    }

    val validationMessages = sentenceValidationService.validateSentences(sortedSentences)
    validationMessages += adjustmentValidationService.validateAdjustments(adjustments)
    validationMessages += recallValidationService.validateFixedTermRecall(sourceData)
    validationMessages += preCalculationValidationService.validatePrePcscDtoDoesNotHaveRemandOrTaggedBail(sourceData)

    return validationMessages
  }

  /*
    Run the validation that can only happen after calculations. e.g. validate that adjustments happen before release date
   */
  fun validateBookingAfterCalculation(
    booking: Booking,
    standardSDSBooking: Booking? = null,
    calculationResult: CalculationResult? = null,
  ): List<ValidationMessage> {
    log.info("Validating booking after calculation")
    val messages = mutableListOf<ValidationMessage>()
    booking.sentenceGroups.forEach { messages += sentenceValidationService.validateSentenceHasNotBeenExtinguished(it) }
    messages += adjustmentValidationService.validateRemandOverlappingRemand(booking)
    messages += adjustmentValidationService.validateRemandOverlappingSentences(standardSDSBooking ?: booking, booking)
    messages += adjustmentValidationService.validateAdditionAdjustmentsInsideLatestReleaseDate(standardSDSBooking ?: booking, booking)
    messages += recallValidationService.validateFixedTermRecallAfterCalc(booking)
    messages += recallValidationService.validateUnsupportedRecallTypes(booking)
    messages += postCalculationValidationService.validateSDSImposedConsecBetweenTrancheDatesForTrancheTwoPrisoner(booking, calculationResult)

    return messages
  }

  internal fun validateSupportedSentencesAndCalculations(sourceData: PrisonApiSourceData): List<ValidationMessage> {
    val sentencesAndOffences = sourceData.sentenceAndOffences
    val sortedSentences = sentencesAndOffences.sortedWith(validationUtilities::sortByCaseNumberAndLineSequence)
    val validationMessages = mutableListOf<ValidationMessage>()
    validationMessages += preCalculationValidationService.validateSupportedSentences(sortedSentences)
    if (validationMessages.isEmpty()) {
      validationMessages += preCalculationValidationService.validateUnsupportedCalculation(sourceData)
    }
    return validationMessages.toList()
  }

  fun validateBeforeCalculation(booking: Booking): List<ValidationMessage> {
    return recallValidationService.validateFixedTermRecall(booking)
  }

  fun validateSentenceForManualEntry(sentences: List<SentenceAndOffence>): MutableList<ValidationMessage> {
    return sentences.map { sentenceValidationService.validateSentenceForManualEntry(it) }.flatten().toMutableList()
  }

  companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }
}
