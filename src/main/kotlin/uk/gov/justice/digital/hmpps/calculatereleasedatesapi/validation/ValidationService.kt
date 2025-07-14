package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationOutput
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationUserInputs
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SupportedValidationResponse
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.CalculationSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAndOffence
import kotlin.collections.plusAssign


@Service
class ValidationService(
  private val preCalculationValidationService: PreCalculationValidationService,
  private val adjustmentValidationService: AdjustmentValidationService,
  private val recallValidationService: RecallValidationService,
  private val sentenceValidationService: SentenceValidationService,
  private val validationUtilities: ValidationUtilities,
  private val postCalculationValidationService: PostCalculationValidationService,
  private val dateValidationService: DateValidationService,
) {

  fun validateBeforeCalculation(
    sourceData: CalculationSourceData,
    excludedValidationTypes: List<ValidationType>
  ): List<ValidationMessage> {
    log.info("Pre-calculation validation of source data")
    val validationMessages = ValidationMessages(excludedValidationTypes = excludedValidationTypes)
    val sentencesAndOffences = sourceData.sentenceAndOffences
    val adjustments = sourceData.bookingAndSentenceAdjustments

    val sortedSentences = sentencesAndOffences.sortedWith(validationUtilities::sortByCaseNumberAndLineSequence)

    validationMessages.addAll(preCalculationValidationService.validateOffenderSupported(sourceData.prisonerDetails))
    if (validationMessages.isNotEmpty()) {
      return validationMessages.messages
    }

    validationMessages.addAll(preCalculationValidationService.hasSentences(sortedSentences))
    if (validationMessages.isNotEmpty()) {
      return validationMessages.messages    }

      validationMessages.addAll(preCalculationValidationService.validateSupportedSentences(sortedSentences))
    if (validationMessages.isNotEmpty()) {
      return validationMessages.messages    }

      validationMessages.addAll(preCalculationValidationService.validateUnsupportedCalculation(sourceData))
    if (validationMessages.isNotEmpty()) {
      return validationMessages.messages    }

      validationMessages.addAll(preCalculationValidationService.validateUnsupportedOffences(sentencesAndOffences))
    if (validationMessages.isNotEmpty()) {
      return validationMessages.messages    }

      validationMessages.addAll(preCalculationValidationService.validateSe20Offences(sourceData))
    if (validationMessages.isNotEmpty()) {
      return validationMessages.messages    }

      validationMessages.addAll(sentenceValidationService.validateSentences(sortedSentences))
  validationMessages.addAll(adjustmentValidationService.validateAdjustmentsBeforeCalculation(adjustments))
      validationMessages.addAll(recallValidationService.validateFixedTermRecall(sourceData))
      validationMessages.addAll(recallValidationService.validateRemandPeriodsAgainstSentenceDates(sourceData))
      validationMessages.addAll(preCalculationValidationService.validatePrePcscDtoDoesNotHaveRemandOrTaggedBail(sourceData))

    return validationMessages.messages
  }

  /*
    Run the validation that can only happen after calculations. e.g. validate that adjustments happen before release date
   */
  fun validateBookingAfterCalculation(
    calculationOutput: CalculationOutput,
    booking: Booking,
    excludedValidationTypes: List<ValidationType>
  ): List<ValidationMessage> {
    log.info("Validating booking after calculation")
    val validationMessages = ValidationMessages(excludedValidationTypes = excludedValidationTypes)

    calculationOutput.sentenceGroup.forEach { validationMessages.addAll(sentenceValidationService.validateSentenceHasNotBeenExtinguished(it)) }
    validationMessages.addAll(adjustmentValidationService.validateRemandOverlappingSentences(calculationOutput, booking))
    validationMessages.addAll(adjustmentValidationService.validateAdditionAdjustmentsInsideLatestReleaseDate(calculationOutput, booking))
    validationMessages.addAll(recallValidationService.validateFixedTermRecallAfterCalc(calculationOutput, booking))
    validationMessages.addAll(validateManualEntryJourneyRequirements(booking, calculationOutput))

    return validationMessages.messages
  }

  fun validateManualEntryJourneyRequirements(
    booking: Booking,
    calculationOutput: CalculationOutput,
  ): List<ValidationMessage> {
    val validationMessages = ValidationMessages(excludedValidationTypes = ValidationType.entries.filterNot { it.isUnsupported() })
    validationMessages.addAll(recallValidationService.validateFtrFortyOverlap(calculationOutput.sentences))
   validationMessages.addAll( recallValidationService.validateUnsupportedRecallTypes(calculationOutput, booking))
    validationMessages.addAll(postCalculationValidationService.validateSDSImposedConsecBetweenTrancheDatesForTrancheTwoPrisoner(booking, calculationOutput))
    validationMessages.addAll(postCalculationValidationService.validateSHPOContainingSX03Offences(booking, calculationOutput))
    return validationMessages.messages
  }

  internal fun validateSupportedSentencesAndCalculations(sourceData: CalculationSourceData): SupportedValidationResponse {
    val sentencesAndOffences = sourceData.sentenceAndOffences
    val sortedSentences = sentencesAndOffences.sortedWith(validationUtilities::sortByCaseNumberAndLineSequence)

    return SupportedValidationResponse(
      preCalculationValidationService.validateSupportedSentences(sortedSentences).distinct(),
      preCalculationValidationService.validateUnsupportedCalculation(sourceData).distinct(),
    )
  }

  fun validateBeforeCalculation(booking: Booking,
                                excludedValidationTypes: List<ValidationType>): List<ValidationMessage> {
    val validationMessages = ValidationMessages(excludedValidationTypes = excludedValidationTypes)
    validationMessages.addAll(recallValidationService.validateFixedTermRecall(booking))
    return validationMessages.messages
  }

  fun validateSentenceForManualEntry(sentences: List<SentenceAndOffence>): MutableList<ValidationMessage> = sentences.map { sentenceValidationService.validateSentenceForManualEntry(it) }.flatten().toMutableList()

  fun validateRequestedDates(dates: List<String>): List<ValidationMessage> = dateValidationService.validateDates(dates)

  companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }
}
