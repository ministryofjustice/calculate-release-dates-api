package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationOutput
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationUserInputs
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.CalculationSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.BookingService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.CalculationService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.CalculationSourceDataService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.InactiveDataOptions
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
    inactiveDataOptions: InactiveDataOptions,
    calculationUserInputs: CalculationUserInputs?,
    validationOrder: ValidationOrder,
  ): List<ValidationMessage> {
    val sourceData = sourceDataService.getCalculationSourceData(prisonerId, inactiveDataOptions)
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
    orders.forEach { order ->
      val validatorsForThisOrder = validators.filter { it.validationOrder() == order }

      val preCalculationSourceDataValidators =
        validatorsForThisOrder.filterIsInstance<PreCalculationSourceDataValidator>()
      messages = preCalculationSourceDataValidators.map { it.validate(sortedSourceData) }.flatten()
      if (messages.isNotEmpty()) return messages

      booking = booking ?: bookingService.getBooking(sourceData)
      val preCalculationBookingValidators = validatorsForThisOrder.filterIsInstance<PreCalculationBookingValidator>()
      messages = preCalculationBookingValidators.map { it.validate(booking) }.flatten()
      if (messages.isNotEmpty()) return messages

      calculationOutput = calculationOutput ?: calculationService.calculateReleaseDates(
        booking,
        calculationUserInputs ?: CalculationUserInputs(),
      )
      val postCalculationValidators = validatorsForThisOrder.filterIsInstance<PostCalculationValidator>()
      messages = postCalculationValidators.map { it.validate(calculationOutput, booking) }.flatten()
      if (messages.isNotEmpty()) return messages
    }

    return messages
  }

  fun validateRequestedDates(dates: List<String>): List<ValidationMessage> = dateValidationService.validateDates(dates)

  fun validateOnlyOffenceDatesForManualEntry(prisonerId: String): List<ValidationMessage> {
    val sourceData = sourceDataService.getCalculationSourceData(prisonerId, InactiveDataOptions.default())
    return sourceData.sentenceAndOffences.mapNotNull { (validators.find { it is SentenceValidator }!! as SentenceValidator).validateWithoutOffenceDate(it) }
  }
}
