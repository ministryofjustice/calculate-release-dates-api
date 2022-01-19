package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.threeten.extra.LocalDateRange
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.BookingTimelineGapException
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
     3. Other adjustments? Tagged bail UAL?
     4. A license recall (TODO)
    */
  fun walkTimelineOfBooking(booking: Booking): Booking {
    val sortedSentences = booking.getAllExtractableSentences().sortedBy { it.sentencedAt }
    val firstSentence = sortedSentences[0]
    var workingRange = firstSentence.getRangeOfSentenceBeforeAwardedDays()
    val totalAda = firstSentence.sentenceCalculation.calculatedTotalAwardedDays
    var previousSentence = firstSentence

    var daysAwardedServed = 0
    var daysRemandNoLongerApplicable = 0

    sortedSentences.forEach {
      val itRange = it.getRangeOfSentenceBeforeAwardedDays()
      if (workingRange.isConnected(itRange)) {
        if (itRange.end.isAfter(workingRange.end)) {
          workingRange = LocalDateRange.of(workingRange.start, itRange.end)
        }
      } else {
        // There is gap here.

        // 1. Check if there have been any ADAs served
        var daysBetween = ChronoUnit.DAYS.between(workingRange.end, it.sentencedAt)
        val daysAdaServed = min(daysBetween - 1, totalAda.toLong())
        daysAwardedServed += daysAdaServed.toInt()
        //Update range to include ada's served.
        workingRange = LocalDateRange.of(workingRange.start, previousSentence.sentenceCalculation.adjustedReleaseDate)

        daysBetween = ChronoUnit.DAYS.between(workingRange.end, it.sentencedAt)
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
          if (workingRange.end.plusDays(daysRemand.toLong()).isBefore(it.sentencedAt.minusDays(1))) {
            throw BookingTimelineGapException("There is a gap between sentences $previousSentence AND $it")
          } else {
            workingRange = LocalDateRange.of(workingRange.start, itRange.end)
          }
        }
      }
      previousSentence = it
      readjustDates(it, daysAwardedServed, daysRemandNoLongerApplicable)
    }
    return booking
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
