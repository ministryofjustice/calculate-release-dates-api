package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.threeten.extra.LocalDateRange
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.SDS40TrancheConfiguration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculableSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Term
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BookingAdjustment
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BookingAdjustmentType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BookingAndSentenceAdjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAdjustment
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAdjustmentType
import java.time.LocalDate

@Service
class AdjustmentValidationService(
  private val trancheConfiguration: SDS40TrancheConfiguration,
) {
  internal fun validateSupportedAdjustments(adjustments: List<BookingAdjustment>): List<ValidationMessage> {
    val messages = mutableListOf<ValidationMessage>()
    if (adjustments.any { it.type == BookingAdjustmentType.LAWFULLY_AT_LARGE }) {
      messages.add(
        ValidationMessage(
          ValidationCode.UNSUPPORTED_ADJUSTMENT_LAWFULLY_AT_LARGE,
        ),
      )
    }
    if (adjustments.any { it.type == BookingAdjustmentType.SPECIAL_REMISSION }) {
      messages.add(
        ValidationMessage(
          ValidationCode.UNSUPPORTED_ADJUSTMENT_SPECIAL_REMISSION,
        ),
      )
    }
    return messages
  }

  internal fun validateAdjustments(adjustments: BookingAndSentenceAdjustments): List<ValidationMessage> {
    val validationMessages =
      adjustments.sentenceAdjustments.mapNotNull { validateSentenceAdjustment(it) }.toMutableList()
    validationMessages.addAll(validateBookingAdjustment(adjustments.bookingAdjustments))
    validationMessages += validateRemandOverlappingRemand(adjustments)
    return validationMessages
  }

  private fun validateSentenceAdjustment(sentenceAdjustment: SentenceAdjustment): ValidationMessage? {
    if (sentenceAdjustment.type == SentenceAdjustmentType.REMAND && (sentenceAdjustment.fromDate == null || sentenceAdjustment.toDate == null)) {
      return ValidationMessage(ValidationCode.REMAND_FROM_TO_DATES_REQUIRED)
    }
    return null
  }

  private fun validateBookingAdjustment(bookingAdjustments: List<BookingAdjustment>): List<ValidationMessage> =
    bookingAdjustments.filter {
      val dateToValidate = if (it.type == BookingAdjustmentType.UNLAWFULLY_AT_LARGE && it.toDate != null) it.toDate else it.fromDate
      BOOKING_ADJUSTMENTS_TO_VALIDATE.contains(it.type) && dateToValidate.isAfter(LocalDate.now())
    }.map { it.type }.distinct().map { ValidationMessage(ADJUSTMENT_FUTURE_DATED_MAP[it]!!) }

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

  private fun logIntersectionWarning(range1: LocalDateRange, range2: LocalDateRange, messageTemplate: String) {
    val args = listOf(range1.toString(), range2.toString())
    log.warn(
      String.format(
        messageTemplate,
        *args.toTypedArray(),
      ),
    )
  }

  private fun buildMessageArguments(range1: LocalDateRange, range2: LocalDateRange): List<String> {
    return listOf(
      range1.start.toString(),
      range1.end.toString(),
      range2.start.toString(),
      range2.end.toString(),
    )
  }

  internal fun validateRemandOverlappingSentences(longestBooking: Booking, booking: Booking): List<ValidationMessage> {
    val sentences = booking.getAllExtractableSentences()
    val longestSentences = longestBooking.getAllExtractableSentences()

    val remandPeriods = booking.adjustments.getOrEmptyList(AdjustmentType.REMAND)

    val validationMessages = mutableListOf<ValidationMessage>()
    if (remandPeriods.isNotEmpty()) {
      val remandRanges = remandPeriods.map { LocalDateRange.of(it.fromDate, it.toDate) }

      val sentenceRanges = this.getRelevantSentenceRanges(sentences, longestSentences)

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

    return validationMessages
  }

  internal fun validateAdditionAdjustmentsInsideLatestReleaseDate(longestBooking: Booking, booking: Booking): List<ValidationMessage> {
    val sentences = booking.getAllExtractableSentences()
    val longestSentences = longestBooking.getAllExtractableSentences()

    // Ensure both lists have the same size before proceeding
    if (sentences.size != longestSentences.size) {
      throw IllegalArgumentException("The number of sentences in longestBooking and booking must be the same.")
    }

    val longestRelevantSentences = this.getLongestRelevantSentence(sentences, longestSentences)

    val latestReleaseDatePreAddedDays =
      longestRelevantSentences.filter { it !is Term }.maxOfOrNull { it.sentenceCalculation.releaseDateWithoutAdditions }
        ?: return emptyList()

    val adas = booking.adjustments.getOrEmptyList(AdjustmentType.ADDITIONAL_DAYS_AWARDED).toSet()
    val radas = booking.adjustments.getOrEmptyList(AdjustmentType.RESTORATION_OF_ADDITIONAL_DAYS_AWARDED).toSet()
    val adjustments = adas + radas

    val adjustmentsAfterRelease =
      adjustments.filter { it.appliesToSentencesFrom.isAfter(latestReleaseDatePreAddedDays) }.toSet()
    if (adjustmentsAfterRelease.isNotEmpty()) {
      val anyAda = adjustmentsAfterRelease.intersect(adas).isNotEmpty()
      val anyRada = adjustmentsAfterRelease.intersect(radas).isNotEmpty()

      if (anyAda) return listOf(ValidationMessage(ValidationCode.ADJUSTMENT_AFTER_RELEASE_ADA))
      if (anyRada) return listOf(ValidationMessage(ValidationCode.ADJUSTMENT_AFTER_RELEASE_RADA))
    }
    return emptyList()
  }

  private fun getLongestRelevantSentence(sentences: List<CalculableSentence>, longestSentences: List<CalculableSentence>): List<CalculableSentence> {
    return sentences.zip(longestSentences).map { (sentence, longestSentence) ->
      if (sentence.sentencedAt.isBefore(trancheConfiguration.trancheTwoCommencementDate)) {
        longestSentence
      } else {
        sentence
      }
    }
  }

  private fun getRelevantSentenceRanges(sentences: List<CalculableSentence>, longestSentences: List<CalculableSentence>): List<LocalDateRange> {
    val longestRelevantSentences = sentences.zip(longestSentences).map { (sentence, longestSentence) ->
      if (sentence.sentenceCalculation.adjustedDeterminateReleaseDate.isBefore(trancheConfiguration.trancheOneCommencementDate)) {
        longestSentence
      } else {
        sentence
      }
    }
    return longestRelevantSentences
      .filter { !it.isRecall() }
      .map {
        LocalDateRange.of(
          it.sentencedAt,
          it.sentenceCalculation.unadjustedDeterminateReleaseDate,
        )
      }
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
