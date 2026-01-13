package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationOutput
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationUserInputs
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.CalculationSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.BookingService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.CalculationService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.CalculationSourceDataService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.SourceDataLookupOptions
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationMessage
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationOrder
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationUtilities
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.validator.PostCalculationValidator
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.validator.PreCalculationBookingValidator
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.validator.PreCalculationSourceDataValidator
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.validator.SentenceValidator
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.validator.Validator

@Service
class ValidationService(
  private val validators: List<Validator>,
  private val bookingService: BookingService,
  private val calculationService: CalculationService,
  private val dateValidationService: DateValidationService,
  private val validationUtilities: ValidationUtilities,
  private val sourceDataService: CalculationSourceDataService,
) {

  fun validate(
    prisonerId: String,
    sourceDataLookupOptions: SourceDataLookupOptions,
    calculationUserInputs: CalculationUserInputs?,
    validationOrder: ValidationOrder,
  ): List<ValidationMessage> {
    val sourceData = sourceDataService.getCalculationSourceData(prisonerId, sourceDataLookupOptions)
    return validate(sourceData, calculationUserInputs, validationOrder)
  }

  fun validate(
    sourceData: CalculationSourceData,
    calculationUserInputs: CalculationUserInputs?,
    validationOrder: ValidationOrder,
  ): List<ValidationMessage> {
    val sortedSourceData = sourceData.copy(sentenceAndOffences = sourceData.sentenceAndOffences.sortedWith(validationUtilities::sortByCaseNumberAndLineSequence))
    val orders = ValidationOrder.entries.filter { it.orderToValidate <= validationOrder.orderToValidate }.sortedBy { it.orderToValidate }

    var booking: Booking? = null
    var calculationOutput: CalculationOutput? = null
    var messages: List<ValidationMessage> = emptyList()
    var calculationFailure: Exception? = null

    orders.forEach { order ->
      val validatorsForThisOrder = validators.filter { it.validationOrder() == order }

      val preCalculationSourceDataValidators =
        validatorsForThisOrder.filterIsInstance<PreCalculationSourceDataValidator>()
      messages = preCalculationSourceDataValidators.flatMap { it.validate(sortedSourceData) }
      if (messages.isNotEmpty()) return messages

      booking = booking ?: bookingService.getBooking(sourceData)
      val preCalculationBookingValidators = validatorsForThisOrder.filterIsInstance<PreCalculationBookingValidator>()
      messages = preCalculationBookingValidators.flatMap { it.validate(booking) }
      if (messages.isNotEmpty()) return messages

      try {
        calculationOutput = calculationOutput ?: calculationService.calculateReleaseDates(
          booking,
          calculationUserInputs ?: CalculationUserInputs(),
        )
        calculationFailure = null
      } catch (e: Exception) {
        // we do not want to return an error on calculation failure if we validate against the failure in a higher order.
        calculationFailure = e
      }
      if (calculationOutput != null) {
        val postCalculationValidators = validatorsForThisOrder.filterIsInstance<PostCalculationValidator>()
        messages = postCalculationValidators.flatMap { it.validate(calculationOutput, booking) }
        if (messages.isNotEmpty()) return messages
      }
    }

    if (messages.isEmpty() && calculationFailure != null) {
      // if there is a calculation error that wasn't handled by a subsequent validator then throw it now
      throw calculationFailure
    }

    return messages
  }

  fun validateRequestedDates(dates: List<String>): List<ValidationMessage> = dateValidationService.validateDates(dates)

  fun validateOnlyOffenceDatesForManualEntry(prisonerId: String): List<ValidationMessage> {
    val sourceData = sourceDataService.getCalculationSourceData(prisonerId, SourceDataLookupOptions.default())
    return sourceData.sentenceAndOffences.mapNotNull { (validators.find { it is SentenceValidator }!! as SentenceValidator).validateWithoutOffenceDate(it) }
  }
}
