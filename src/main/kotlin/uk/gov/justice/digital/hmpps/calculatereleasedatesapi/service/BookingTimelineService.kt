package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.threeten.extra.LocalDateRange
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.BookingTimelineGapException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.RemandPeriodOverlapsWithRemandException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.RemandPeriodOverlapsWithSentenceException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ExtractableSentence
import java.time.temporal.ChronoUnit
import kotlin.math.min

@Service
class BookingTimelineService(
  val sentenceCalculationService: SentenceCalculationService
) {
  /*
     This method walks through the timeline of all the booking, checking for gaps between the release date and the
     next sentence date of all the extractable sentences.
     If there is a gap, it must be filled by any of:
     1. Served ADAs,
     2. Remand, i.e. A prisoner is kept on remand for an upcoming court date
     3. Other adjustments? Tagged bail UAL? (TODO)
     4. A license recall (TODO)
    */
  fun walkTimelineOfBooking(booking: Booking): Booking {
    val sortedSentences = booking.getAllExtractableSentences().sortedBy { it.sentencedAt }
    val firstSentence = sortedSentences[0]
    var sentenceRange = firstSentence.getRangeOfSentenceBeforeAwardedDays()
    val totalAda = firstSentence.sentenceCalculation.calculatedTotalAwardedDays
    var previousSentence = firstSentence

    var daysAwardedServed = 0
    var daysRemandNoLongerApplicable = 0

    sortedSentences.forEach {
      val itRange = it.getRangeOfSentenceBeforeAwardedDays()
      if (sentenceRange.isConnected(itRange)) {
        if (itRange.end.isAfter(sentenceRange.end)) {
          sentenceRange = LocalDateRange.of(sentenceRange.start, itRange.end)
        }
      } else {
        // There is gap here.

        // 1. Check if there have been any ADAs served
        var daysBetween = ChronoUnit.DAYS.between(sentenceRange.end, it.sentencedAt)
        val daysAdaServed = min(daysBetween - 1, totalAda.toLong())
        daysAwardedServed += daysAdaServed.toInt()
        // Update range to include ada's served.
        sentenceRange = LocalDateRange.of(sentenceRange.start, previousSentence.sentenceCalculation.adjustedReleaseDate)

        daysBetween = ChronoUnit.DAYS.between(sentenceRange.end, it.sentencedAt)
        if (daysBetween > 0) {
          // There is still a gap

          // 2. A release date has occurred but there are more sentences on the booking, therefore previous adjustments
          // should be wiped. TODO only remand?
          daysRemandNoLongerApplicable += booking.getOrZero(
            AdjustmentType.REMAND,
            previousSentence.sentenceCalculation.adjustedReleaseDate
          )

          // 3. A release date has occurred but there are more sentences on the booking, therefore that gap should
          // be filled by remand.
          val daysRemand = booking.getOrZero(AdjustmentType.REMAND, it.sentencedAt) - daysRemandNoLongerApplicable
          if (sentenceRange.end.plusDays(daysRemand.toLong()).isBefore(it.sentencedAt.minusDays(1))) {
            throw BookingTimelineGapException(
              "There is a gap between sentence sentence at ${previousSentence.sentencedAt}" +
                " release of ${previousSentence.sentenceCalculation.adjustedReleaseDate}" +
                " AND sentence at ${it.sentencedAt} release of ${it.sentenceCalculation.adjustedReleaseDate}"
            )
          } else {
            sentenceRange = LocalDateRange.of(sentenceRange.start, itRange.end)
          }
        }
      }
      previousSentence = it
      readjustDates(it, daysAwardedServed, daysRemandNoLongerApplicable)
    }

    validateRemandPeriodsOverlapping(booking)
    return booking
  }

  private fun validateRemandPeriodsOverlapping(booking: Booking) {
    val remandPeriods = booking.get(AdjustmentType.REMAND)
    if (remandPeriods.isNotEmpty()) {
      val remandRanges = remandPeriods.map { LocalDateRange.of(it.fromDate, it.toDate) }
      val sentenceRanges = booking.getAllExtractableSentences().map { LocalDateRange.of(it.sentencedAt, it.sentenceCalculation.adjustedReleaseDate) }

      val allRanges = (remandRanges + sentenceRanges).sortedBy { it.start }
      var totalRange: LocalDateRange? = null
      var previousRangeIsRemand: Boolean? = null
      var previousRange: LocalDateRange? = null

      allRanges.forEach {
        val isRemand = remandRanges.any() { sentenceRange -> sentenceRange === it }
        if (totalRange == null && previousRangeIsRemand == null) {
          totalRange = it
          previousRangeIsRemand = isRemand
          previousRange = it
        } else {
          if (it.isConnected(totalRange) &&
            (previousRangeIsRemand!! || isRemand)
          ) {
            // Remand overlaps
            if (previousRangeIsRemand!! && isRemand) {
              throw RemandPeriodOverlapsWithRemandException("Remand of range ${previousRange!!} overlaps with remand of range $it")
            } else {
              throw RemandPeriodOverlapsWithSentenceException("${if (previousRangeIsRemand!!) "Remand" else "Sentence"} of range ${previousRange!!} overlaps with ${if (isRemand) "remand" else "sentence"} of range $it")
            }
          }
          if (it.end.isAfter(totalRange!!.end)) {
            totalRange = LocalDateRange.of(totalRange!!.start, it.end)
          }
          previousRangeIsRemand = isRemand
          previousRange = it
        }
      }
    }
  }

  private fun readjustDates(it: ExtractableSentence, daysAwardedServed: Int, daysRemandNoLongerApplicable: Int) {
    if (daysAwardedServed + daysRemandNoLongerApplicable != 0) {
      it.sentenceCalculation.calculatedTotalAwardedDays =
        it.sentenceCalculation.calculatedTotalAwardedDays - daysAwardedServed
      it.sentenceCalculation.calculatedTotalDeductedDays =
        it.sentenceCalculation.calculatedTotalDeductedDays - daysRemandNoLongerApplicable
      sentenceCalculationService.calculateDatesFromAdjustments(it)
    }
  }

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}
