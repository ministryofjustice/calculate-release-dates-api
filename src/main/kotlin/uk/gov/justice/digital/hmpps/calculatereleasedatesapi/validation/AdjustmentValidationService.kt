package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.threeten.extra.LocalDateRange
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.SDS40TrancheConfiguration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Adjustment
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

  internal fun validateIfAdjustmentsAreSupported(adjustments: List<BookingAdjustment>): List<ValidationMessage> {
    return mutableListOf<ValidationMessage>().apply {
      addAll(lawfullyAtLargeIsNotSupported(adjustments))
      addAll(specialRemissionIsNotSupported(adjustments))
    }
  }

  internal fun throwErrorIfAdditionAdjustmentsAfterLatestReleaseDate(adjustments: BookingAndSentenceAdjustments): List<ValidationMessage> {
    return mutableListOf<ValidationMessage>().apply {
      addAll(validateAllSentenceAdjustmentsHaveFromOrToDates(adjustments))
      addAll(validateBookingAdjustment(adjustments.bookingAdjustments))
      addAll(validateRemandOverlappingRemand(adjustments))
    }
  }

  private fun validateAllSentenceAdjustmentsHaveFromOrToDates(adjustments: BookingAndSentenceAdjustments): List<ValidationMessage> {
    return adjustments.sentenceAdjustments.flatMap { validateSentenceAdjustmentHaveFromOrToDates(it) }.toMutableList()
  }

  private fun validateSentenceAdjustmentHaveFromOrToDates(sentenceAdjustment: SentenceAdjustment): List<ValidationMessage> {
    return if (sentenceAdjustment.type == SentenceAdjustmentType.REMAND &&
      (sentenceAdjustment.fromDate == null || sentenceAdjustment.toDate == null)
    ) {
      listOf(ValidationMessage(ValidationCode.REMAND_FROM_TO_DATES_REQUIRED))
    } else {
      emptyList()
    }
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

  internal fun validateAdditionAdjustmentsInsideLatestReleaseDate(longestBooking: Booking, booking: Booking): List<ValidationMessage> {
    val sentences = booking.getAllExtractableSentences()
    val longestSentences = longestBooking.getAllExtractableSentences()

    validateSentenceCounts(sentences, longestSentences)

    val latestReleaseDate = getLatestReleaseDateWithoutAdditions(sentences, longestSentences) ?: return emptyList()

    val adjustments = getSortedAdjustments(booking)
    return throwErrorIfAdditionAdjustmentsAfterLatestReleaseDate(adjustments, latestReleaseDate)
  }

  private fun validateSentenceCounts(sentences: List<CalculableSentence>, longestSentences: List<CalculableSentence>) {
    if (sentences.size != longestSentences.size) {
      log.error("$sentences is not the same length as $longestSentences")
      throw IllegalArgumentException("The number of sentences in longestBooking and booking must be the same.")
    }
  }

  private fun getLatestReleaseDateWithoutAdditions(sentences: List<CalculableSentence>, longestSentences: List<CalculableSentence>): LocalDate? {
    val longestRelevantSentences = getLongestRelevantSentence(sentences, longestSentences)
    return longestRelevantSentences
      .filter { it !is Term }
      .maxOfOrNull { it.sentenceCalculation.releaseDateWithoutAdditions }
  }

  private fun getSortedAdjustments(booking: Booking): List<Pair<AdjustmentType, Adjustment>> {
    val adas = booking.adjustments.getOrEmptyList(AdjustmentType.ADDITIONAL_DAYS_AWARDED)
      .map { AdjustmentType.ADDITIONAL_DAYS_AWARDED to it }

    val radas = booking.adjustments.getOrEmptyList(AdjustmentType.RESTORATION_OF_ADDITIONAL_DAYS_AWARDED)
      .map { AdjustmentType.RESTORATION_OF_ADDITIONAL_DAYS_AWARDED to it }

    return (adas + radas).sortedBy { it.second.appliesToSentencesFrom }
  }

  private fun throwErrorIfAdditionAdjustmentsAfterLatestReleaseDate(adjustments: List<Pair<AdjustmentType, Adjustment>>, initialReleaseDate: LocalDate): List<ValidationMessage> {
    var latestReleaseDate = initialReleaseDate
    val messages = mutableSetOf<ValidationMessage>()

    adjustments.forEach { (type, adjustment) ->
      messages.addAll(adaOrRadaAfterSentenceLength(type, adjustment, latestReleaseDate))
      latestReleaseDate = applyAdjustment(type, adjustment, latestReleaseDate)
    }

    return messages.toList() // Convert set back to list
  }

  private fun adaOrRadaAfterSentenceLength(type: AdjustmentType, adjustment: Adjustment, latestReleaseDate: LocalDate): List<ValidationMessage> {
    if (adjustment.appliesToSentencesFrom.isAfter(latestReleaseDate)) {
      return listOf(
        if (type == AdjustmentType.ADDITIONAL_DAYS_AWARDED) {
          ValidationMessage(ValidationCode.ADJUSTMENT_AFTER_RELEASE_ADA)
        } else {
          ValidationMessage(ValidationCode.ADJUSTMENT_AFTER_RELEASE_RADA)
        },
      )
    }
    return emptyList()
  }

  private fun applyAdjustment(type: AdjustmentType, adjustment: Adjustment, releaseDate: LocalDate): LocalDate {
    return if (type == AdjustmentType.ADDITIONAL_DAYS_AWARDED) {
      releaseDate.plusDays(adjustment.numberOfDays.toLong())
    } else {
      releaseDate.minusDays(adjustment.numberOfDays.toLong())
    }
  }

  private fun lawfullyAtLargeIsNotSupported(adjustments: List<BookingAdjustment>): List<ValidationMessage> {
    return if (adjustments.any { it.type == BookingAdjustmentType.LAWFULLY_AT_LARGE }) {
      listOf(ValidationMessage(ValidationCode.UNSUPPORTED_ADJUSTMENT_LAWFULLY_AT_LARGE))
    } else {
      emptyList()
    }
  }

  private fun specialRemissionIsNotSupported(adjustments: List<BookingAdjustment>): List<ValidationMessage> {
    return if (adjustments.any { it.type == BookingAdjustmentType.SPECIAL_REMISSION }) {
      listOf(ValidationMessage(ValidationCode.UNSUPPORTED_ADJUSTMENT_SPECIAL_REMISSION))
    } else {
      emptyList()
    }
  }

  private fun validateBookingAdjustment(bookingAdjustments: List<BookingAdjustment>): List<ValidationMessage> =
    bookingAdjustments.filter {
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

    val validationMessages = mutableSetOf<ValidationMessage>()
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

    return validationMessages.toList()
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

  // Range is 'sentenced at' to 'Release Date (CRD or ARD)'
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
          it.sentenceCalculation.adjustedDeterminateReleaseDate,
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
