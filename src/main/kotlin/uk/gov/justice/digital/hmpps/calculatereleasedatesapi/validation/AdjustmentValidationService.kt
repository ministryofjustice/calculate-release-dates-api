package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.threeten.extra.LocalDateRange
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Adjustment
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationOutput
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Term
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BookingAdjustment
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BookingAdjustmentType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAdjustment
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAdjustmentType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi.BookingAndSentenceAdjustments
import java.time.LocalDate

@Service
class AdjustmentValidationService {

  internal fun validateIfAdjustmentsAreSupported(adjustments: List<BookingAdjustment>): List<ValidationMessage> = mutableListOf<ValidationMessage>().apply {
    addAll(lawfullyAtLargeIsNotSupported(adjustments))
    addAll(specialRemissionIsNotSupported(adjustments))
  }

  internal fun throwErrorIfAdditionAdjustmentsAfterLatestReleaseDate(adjustments: BookingAndSentenceAdjustments): List<ValidationMessage> = mutableListOf<ValidationMessage>().apply {
    addAll(validateAllSentenceAdjustmentsHaveFromOrToDates(adjustments))
    addAll(validateBookingAdjustment(adjustments.bookingAdjustments))
    addAll(validateRemandOverlappingRemand(adjustments))
  }

  private fun validateAllSentenceAdjustmentsHaveFromOrToDates(adjustments: BookingAndSentenceAdjustments): List<ValidationMessage> = adjustments.sentenceAdjustments.flatMap { validateSentenceAdjustmentHaveFromOrToDates(it) }.toMutableList()

  private fun validateSentenceAdjustmentHaveFromOrToDates(sentenceAdjustment: SentenceAdjustment): List<ValidationMessage> = if (sentenceAdjustment.type == SentenceAdjustmentType.REMAND &&
    (sentenceAdjustment.fromDate == null || sentenceAdjustment.toDate == null)
  ) {
    listOf(ValidationMessage(ValidationCode.REMAND_FROM_TO_DATES_REQUIRED))
  } else {
    emptyList()
  }

  internal fun validateRemandOverlappingRemand(booking: Booking): List<ValidationMessage> {
    val remandPeriods = booking.adjustments.getOrEmptyList(AdjustmentType.REMAND)

    val validationMessages = mutableListOf<ValidationMessage>()
    if (remandPeriods.isNotEmpty()) {
      val remandRanges = remandPeriods.map { LocalDateRange.of(it.fromDate, it.toDate) }

      remandRanges.forEachIndexed { index, remandRange ->
        remandRanges.drop(index + 1).forEach { otherRemandRange ->
          if (remandRange.isConnected(otherRemandRange)) {
            logIntersectionWarning(remandRange, otherRemandRange, "Remand of range %s overlaps with other remand of range %s")
            validationMessages.add(
              ValidationMessage(
                ValidationCode.REMAND_OVERLAPS_WITH_REMAND,
                arguments = buildMessageArguments(remandRange, otherRemandRange),
              ),
            )
          }
        }
      }
    }

    return validationMessages
  }

  internal fun validateAdditionAdjustmentsInsideLatestReleaseDate(calculationOutput: CalculationOutput, booking: Booking): List<ValidationMessage> {
    val adjustments = getSortedAdjustments(booking)
    val latestReleaseDate = calculationOutput.custodialPeriod.mapNotNull { period ->
      period.sentences
        .filterNot { it is Term }
        .maxOfOrNull { it.sentenceCalculation.releaseDateDefaultedByCommencement }
    }.maxOrNull()

    if (latestReleaseDate !is LocalDate) {
      log.warn("validateAdditionAdjustmentsInsideLatestReleaseDate: No release date found for sentences within timeline calculation")
      return emptyList()
    }

    val messages = mutableSetOf<ValidationMessage>()

    adjustments.forEach { (type, adjustment) ->
      if (adjustment.appliesToSentencesFrom.isAfter(latestReleaseDate)) {
        if (type == AdjustmentType.ADDITIONAL_DAYS_AWARDED) {
          messages.add(ValidationMessage(ValidationCode.ADJUSTMENT_AFTER_RELEASE_ADA))
        } else {
          messages.add(ValidationMessage(ValidationCode.ADJUSTMENT_AFTER_RELEASE_RADA))
        }
      }
    }
    return messages.toList()
  }

  private fun getSortedAdjustments(booking: Booking): List<Pair<AdjustmentType, Adjustment>> {
    val adas = booking.adjustments.getOrEmptyList(AdjustmentType.ADDITIONAL_DAYS_AWARDED)
      .map { AdjustmentType.ADDITIONAL_DAYS_AWARDED to it }

    val radas = booking.adjustments.getOrEmptyList(AdjustmentType.RESTORATION_OF_ADDITIONAL_DAYS_AWARDED)
      .map { AdjustmentType.RESTORATION_OF_ADDITIONAL_DAYS_AWARDED to it }

    return (adas + radas).sortedBy { it.second.appliesToSentencesFrom }
  }

  private fun lawfullyAtLargeIsNotSupported(adjustments: List<BookingAdjustment>): List<ValidationMessage> = if (adjustments.any { it.type == BookingAdjustmentType.LAWFULLY_AT_LARGE }) {
    listOf(ValidationMessage(ValidationCode.UNSUPPORTED_ADJUSTMENT_LAWFULLY_AT_LARGE))
  } else {
    emptyList()
  }

  private fun specialRemissionIsNotSupported(adjustments: List<BookingAdjustment>): List<ValidationMessage> = if (adjustments.any { it.type == BookingAdjustmentType.SPECIAL_REMISSION }) {
    listOf(ValidationMessage(ValidationCode.UNSUPPORTED_ADJUSTMENT_SPECIAL_REMISSION))
  } else {
    emptyList()
  }

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

  private fun buildMessageArguments(range1: LocalDateRange, range2: LocalDateRange): List<String> = listOf(
    range1.start.toString(),
    range1.end.toString(),
    range2.start.toString(),
    range2.end.toString(),
  )

  internal fun validateRemandOverlappingSentences(calculationOutput: CalculationOutput, booking: Booking): List<ValidationMessage> {
    val remandPeriods = booking.adjustments.getOrEmptyList(AdjustmentType.REMAND)

    val validationMessages = mutableSetOf<ValidationMessage>()
    if (remandPeriods.isNotEmpty()) {
      val remandRanges = remandPeriods.map { LocalDateRange.of(it.fromDate, it.toDate) }

      val sentenceRanges = calculationOutput.custodialPeriod.filter { period -> period.sentences.none { it.isRecall() } }.map { LocalDateRange.of(it.from, it.to) }

      remandRanges.forEach { remandRange ->
        sentenceRanges.forEach { sentenceRange ->
          if (remandRange.isConnected(sentenceRange)) {
            logIntersectionWarning(remandRange, sentenceRange, "Remand of range %s overlaps with sentence of range %s")
            validationMessages.add(
              ValidationMessage(
                ValidationCode.REMAND_OVERLAPS_WITH_SENTENCE,
                arguments = buildMessageArguments(sentenceRange, remandRange),
              ),
            )
          }
        }
      }
    }

    return validationMessages.toList()
  }

  private fun validateRemandOverlappingRemand(adjustments: BookingAndSentenceAdjustments): List<ValidationMessage> {
    val remandPeriods =
      adjustments.sentenceAdjustments.filter { it.type == SentenceAdjustmentType.REMAND && it.fromDate != null && it.toDate != null }
    if (remandPeriods.isNotEmpty()) {
      val remandRanges = remandPeriods.map { LocalDateRange.of(it.fromDate, it.toDate) }

      var totalRange: LocalDateRange? = null

      remandRanges.forEach {
        if (totalRange == null) {
          totalRange = it
        } else if (it.isConnected(totalRange)) {
          val messageArgs =
            listOf(it.start.toString(), it.end.toString(), totalRange!!.start.toString(), totalRange!!.end.toString())
          return listOf(ValidationMessage(ValidationCode.REMAND_OVERLAPS_WITH_REMAND, arguments = messageArgs))
        }
      }
    }
    return emptyList()
  }

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

    private val log = LoggerFactory.getLogger(this::class.java)
  }
}
