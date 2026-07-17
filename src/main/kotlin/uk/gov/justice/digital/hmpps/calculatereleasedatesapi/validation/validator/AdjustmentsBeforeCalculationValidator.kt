package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.validator

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.threeten.extra.LocalDateRange
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.adjustmentsapi.model.AdjustmentDto
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BookingAdjustment
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BookingAdjustmentType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.CalculationSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAdjustmentType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.getHumanReadableAdjustmentType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi.BookingAndSentenceAdjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationMessage
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationOrder
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationUtilities
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Component
class AdjustmentsBeforeCalculationValidator(private val validationUtilities: ValidationUtilities, @Value($$"${adjustments.ui.url}") private val adjustmentsUiUrl: String) : PreCalculationSourceDataValidator {

  override fun validate(sourceData: CalculationSourceData): List<ValidationMessage> = sourceData.bookingAndSentenceAdjustments.fold(
    { validateAdjustmentsBeforeCalculation(it, sourceData) },
    { validateAdjustmentsBeforeCalculation(it, sourceData) },
  )

  internal fun validateAdjustmentsBeforeCalculation(adjustments: BookingAndSentenceAdjustments, sourceData: CalculationSourceData): List<ValidationMessage> = mutableListOf<ValidationMessage>().apply {
    addAll(validateAllRemandHasFromAndToDates(adjustments))
    addAll(validateBookingAdjustment(adjustments.bookingAdjustments))
    val (validAdjustment, invalidAdjustments) = adjustments.sentenceAdjustments
      .filter { it.type == SentenceAdjustmentType.REMAND && it.fromDate != null && it.toDate != null }
      .partition { it.fromDate!! <= it.toDate }

    if (invalidAdjustments.isNotEmpty()) {
      addAll(
        invalidAdjustments.map {
          ValidationMessage(
            ValidationCode.ADJUSTMENT_INVALID_DATE_RANGE,
            listOf(
              it.getHumanReadableAdjustmentType(),
              it.fromDate!!.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
              it.toDate!!.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
            ),
          )
        },
      )
    }

    addAll(validateRemandOverlappingRemand(validAdjustment.map { LocalDateRange.of(it.fromDate!!, it.toDate!!) }))
    addAll(validateAllAdjustmentsAreLinkedToCurrentSentences(adjustments.sentenceAdjustments.map { adjustment -> adjustment.sentenceSequence }.toSet(), sourceData))
  }

  internal fun validateAdjustmentsBeforeCalculation(adjustments: List<AdjustmentDto>, sourceData: CalculationSourceData): List<ValidationMessage> = mutableListOf<ValidationMessage>().apply {
    addAll(validateAllRemandHasFromAndToDates(adjustments))
    addAll(validateAdjustmentFutureDates(adjustments))
    addAll(
      validateRemandOverlappingRemand(
        adjustments
          .filter { it.adjustmentType == AdjustmentDto.AdjustmentType.REMAND && it.fromDate != null && it.toDate != null }
          .map { LocalDateRange.of(it.fromDate!!, it.toDate!!) },
      ),
    )
    addAll(validateAllAdjustmentsAreLinkedToCurrentSentences(adjustments.mapNotNull { adjustment -> adjustment.sentenceSequence }.toSet(), sourceData))
  }

  private fun validateAdjustmentFutureDates(adjustments: List<AdjustmentDto>): List<ValidationMessage> = adjustments.filter {
    val dateToValidate = if (it.adjustmentType == AdjustmentDto.AdjustmentType.UNLAWFULLY_AT_LARGE && it.toDate != null) it.toDate else it.fromDate
    FUTURE_DATED_ADJUSTMENT_TYPES_TO_VALIDATE.contains(it.adjustmentType) && dateToValidate!!.isAfter(LocalDate.now())
  }.map { it.adjustmentType }.distinct().map { ValidationMessage(FUTURE_DATED_ADJUSTMENT_TYPES_MAP[it]!!) }

  private fun validateBookingAdjustment(bookingAdjustments: List<BookingAdjustment>): List<ValidationMessage> = bookingAdjustments.filter {
    val dateToValidate = if (it.type == BookingAdjustmentType.UNLAWFULLY_AT_LARGE && it.toDate != null) it.toDate else it.fromDate
    BOOKING_ADJUSTMENTS_TO_VALIDATE.contains(it.type) && dateToValidate.isAfter(LocalDate.now())
  }.map { it.type }.distinct().map { ValidationMessage(ADJUSTMENT_FUTURE_DATED_MAP[it]!!) }

  private fun logIntersectionWarning(range1: LocalDateRange, range2: LocalDateRange, messageTemplate: String) {
    val args = listOf(range1.toString(), range2.toString())
    log.warn(
      String.format(
        messageTemplate,
        *args.toTypedArray(),
      ),
    )
  }

  private fun validateAllRemandHasFromAndToDates(adjustments: List<AdjustmentDto>): List<ValidationMessage> = adjustments.filter { it.adjustmentType == AdjustmentDto.AdjustmentType.REMAND }.flatMap {
    if (it.fromDate == null || it.toDate == null) {
      listOf(ValidationMessage(ValidationCode.REMAND_FROM_TO_DATES_REQUIRED))
    } else {
      emptyList()
    }
  }

  private fun validateAllRemandHasFromAndToDates(adjustments: BookingAndSentenceAdjustments): List<ValidationMessage> = adjustments.sentenceAdjustments.filter { it.type == SentenceAdjustmentType.REMAND }.flatMap {
    if (it.fromDate == null || it.toDate == null) {
      listOf(ValidationMessage(ValidationCode.REMAND_FROM_TO_DATES_REQUIRED))
    } else {
      emptyList()
    }
  }

  private fun validateRemandOverlappingRemand(remandRanges: List<LocalDateRange>): List<ValidationMessage> {
    val validationMessages = mutableListOf<ValidationMessage>()
    remandRanges.forEachIndexed { index, remandRange ->
      remandRanges.drop(index + 1).forEach { otherRemandRange ->
        if (remandRange.isConnected(otherRemandRange)) {
          logIntersectionWarning(remandRange, otherRemandRange, "Remand of range %s overlaps with other remand of range %s")
          validationMessages.add(
            ValidationMessage(
              ValidationCode.REMAND_OVERLAPS_WITH_REMAND,
              arguments = validationUtilities.buildOverlappingMessageArguments(remandRange, otherRemandRange),
            ),
          )
        }
      }
    }
    return validationMessages
  }

  fun validateAllAdjustmentsAreLinkedToCurrentSentences(sentenceSequences: Set<Int>, sourceData: CalculationSourceData): List<ValidationMessage> {
    val sentenceAndOffences = sourceData.sentenceAndOffences
    if (sentenceSequences.any { sentenceSequence -> sentenceAndOffences.none { it.sentenceSequence == sentenceSequence } }) {
      return listOf(ValidationMessage(ValidationCode.ADJUSTMENT_LINKED_TO_INACTIVE_SENTENCE, listOf(adjustmentsUiUrl, sourceData.prisonerDetails.offenderNo)))
    }
    return emptyList()
  }

  override fun validationOrder() = ValidationOrder.INVALID

  companion object {
    private val BOOKING_ADJUSTMENTS_TO_VALIDATE =
      listOf(
        BookingAdjustmentType.ADDITIONAL_DAYS_AWARDED,
        BookingAdjustmentType.UNLAWFULLY_AT_LARGE,
        BookingAdjustmentType.RESTORED_ADDITIONAL_DAYS_AWARDED,
      )
    private val ADJUSTMENT_FUTURE_DATED_MAP = mapOf(
      BookingAdjustmentType.ADDITIONAL_DAYS_AWARDED to ValidationCode.ADJUSTMENT_FUTURE_DATED_ADA,
      BookingAdjustmentType.UNLAWFULLY_AT_LARGE to ValidationCode.ADJUSTMENT_FUTURE_DATED_UAL,
      BookingAdjustmentType.RESTORED_ADDITIONAL_DAYS_AWARDED to ValidationCode.ADJUSTMENT_FUTURE_DATED_RADA,
    )
    private val FUTURE_DATED_ADJUSTMENT_TYPES_TO_VALIDATE =
      listOf(
        AdjustmentDto.AdjustmentType.ADDITIONAL_DAYS_AWARDED,
        AdjustmentDto.AdjustmentType.UNLAWFULLY_AT_LARGE,
        AdjustmentDto.AdjustmentType.RESTORATION_OF_ADDITIONAL_DAYS_AWARDED,
      )
    private val FUTURE_DATED_ADJUSTMENT_TYPES_MAP = mapOf(
      AdjustmentDto.AdjustmentType.ADDITIONAL_DAYS_AWARDED to ValidationCode.ADJUSTMENT_FUTURE_DATED_ADA,
      AdjustmentDto.AdjustmentType.UNLAWFULLY_AT_LARGE to ValidationCode.ADJUSTMENT_FUTURE_DATED_UAL,
      AdjustmentDto.AdjustmentType.RESTORATION_OF_ADDITIONAL_DAYS_AWARDED to ValidationCode.ADJUSTMENT_FUTURE_DATED_RADA,
    )

    private val log = LoggerFactory.getLogger(this::class.java)
  }
}
