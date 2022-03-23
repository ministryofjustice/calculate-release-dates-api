package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.threeten.extra.LocalDateRange
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType.ADDITIONAL_DAYS_SERVED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType.REMAND
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.RemandPeriodOverlapsWithRemandException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.RemandPeriodOverlapsWithSentenceException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Adjustment
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ExtractableSentence
import java.time.LocalDate
import java.time.temporal.ChronoUnit.DAYS
import kotlin.math.min

@Service
class BookingTimelineService(
  val sentenceCalculationService: SentenceCalculationService
) {
  /*
     This method walks through the timeline of all the booking, checking for gaps between the release date (before ADAs)
     and the next sentence date of all the extractable sentences.
     If there is a gap, it must be filled by any of:
     1. Served ADAs
     2. Remand
     3. Tagged bail
     4. A license recall (TODO)
     5. Untagged bail or other forms of bail that don't get recorded in NOMIS (out of scope)
    */
  fun walkTimelineOfBooking(booking: Booking): Booking {
    log.info("Building timeline of sentences")
    val sortedSentences = booking.getAllExtractableSentences().sortedBy { it.sentencedAt }
    val firstSentence = sortedSentences[0]
    var sentenceRange = firstSentence.getRangeOfSentenceBeforeAwardedDays()
    val totalAda = firstSentence.sentenceCalculation.calculatedTotalAwardedDays
    var previousSentence = firstSentence

    // Which sentences are in the same "Group". A "Group" of sentences are sentences that are concurrent to each other,
    // and there sentenceAt dates overlap with the other release date. i.e. there is no release inbetween them
    // Whenever a release happens a new group is started.
    val sentencesInGroup: MutableList<ExtractableSentence> = mutableListOf()
    var previousReleaseDateReached: LocalDate? = null

    sortedSentences.forEach {
      it.sentenceCalculation.adjustmentsBefore = sentenceRange.end
      it.sentenceCalculation.adjustmentsAfter = previousReleaseDateReached
      val itRange = it.getRangeOfSentenceBeforeAwardedDays()
      if (previousSentence.sentenceType.isRecall && !it.sentenceType.isRecall) {
        // The last sentence was a recall, this one is not. Treat this as release in-between so that adjustments are not shared.
        previousReleaseDateReached = it.sentencedAt.minusDays(1)
        shareAdjustmentsThroughSentenceGroup(sentencesInGroup, sentenceRange.end)
        // Clear the sentence group and start again.
        sentencesInGroup.clear()
        sentencesInGroup.add(it)
        it.sentenceCalculation.adjustmentsBefore = it.sentencedAt
        it.sentenceCalculation.adjustmentsAfter = previousReleaseDateReached
        sentenceRange = LocalDateRange.of(sentenceRange.start, itRange.end)
      } else if (sentenceRange.isConnected(itRange)) {
        if (itRange.end.isAfter(sentenceRange.end)) {
          sentenceRange = LocalDateRange.of(sentenceRange.start, itRange.end)
        }
        sentencesInGroup.add(it)
      } else {
        // There is gap here.
        // 1. Check if there have been any ADAs served
        var daysBetween = DAYS.between(sentenceRange.end, it.sentencedAt)
        val daysAdaServed = min(daysBetween - 1, totalAda.toLong())
        booking.adjustments.addAdjustment(
          ADDITIONAL_DAYS_SERVED,
          Adjustment(
            numberOfDays = daysAdaServed.toInt(),
            appliesToSentencesFrom = it.sentencedAt
          )
        )

        daysBetween = DAYS.between(sentenceRange.end.plusDays(daysAdaServed), it.sentencedAt)
        if (daysBetween <= 1) {
          // The gap has been filled by served adas.
          sentenceRange = LocalDateRange.of(sentenceRange.start, itRange.end)
          sentencesInGroup.add(it)
        } else {
          // There is still a gap

          // 2. A release date has occurred but there are more sentences on the booking, therefore previous deductions
          // should be wiped.
          previousReleaseDateReached = previousSentence.sentenceCalculation.releaseDate
          log.info("A release occurred in booking timeline at ${previousReleaseDateReached!!}")

          // This is the ends of the sentence group. Make sure all sentences share the adjustments in this group.
          shareAdjustmentsThroughSentenceGroup(sentencesInGroup, sentenceRange.end)
          // Clear the sentence group and start again.
          sentencesInGroup.clear()
          sentencesInGroup.add(it)
          it.sentenceCalculation.adjustmentsBefore = it.sentencedAt
          it.sentenceCalculation.adjustmentsAfter = previousReleaseDateReached
          sentenceRange = LocalDateRange.of(sentenceRange.start, itRange.end)
        }
      }
      previousSentence = it
    }
    shareAdjustmentsThroughSentenceGroup(sentencesInGroup, sentenceRange.end)

    validateRemandPeriodsOverlapping(booking)
    return booking
  }

  private fun shareAdjustmentsThroughSentenceGroup(sentencesInGroup: List<ExtractableSentence>, endOfGroup: LocalDate) {
    sentencesInGroup.forEach {
      it.sentenceCalculation.adjustmentsBefore = endOfGroup
      readjustDates(it)
    }
  }

  private fun validateRemandPeriodsOverlapping(booking: Booking) {
    val remandPeriods = booking.adjustments.getOrEmptyList(REMAND)
    if (remandPeriods.isNotEmpty()) {
      val remandRanges = remandPeriods.map { LocalDateRange.of(it.fromDate, it.toDate) }
      val sentenceRanges = booking.getAllExtractableSentences().map { LocalDateRange.of(it.sentencedAt, it.sentenceCalculation.adjustedDeterminateReleaseDate) }

      val allRanges = (remandRanges + sentenceRanges).sortedBy { it.start }
      var totalRange: LocalDateRange? = null
      var previousRangeIsRemand: Boolean? = null
      var previousRange: LocalDateRange? = null

      allRanges.forEach {
        val isRemand = remandRanges.any { sentenceRange -> sentenceRange === it }
        if (totalRange == null && previousRangeIsRemand == null) {
          totalRange = it
        } else if (it.isConnected(totalRange) &&
          (previousRangeIsRemand!! || isRemand)
        ) {
          // Remand overlaps
          if (previousRangeIsRemand!! && isRemand) {
            throw RemandPeriodOverlapsWithRemandException("Remand of range ${previousRange!!} overlaps with remand of range $it")
          } else {
            throw RemandPeriodOverlapsWithSentenceException("${if (previousRangeIsRemand!!) "Remand" else "Sentence"} of range ${previousRange!!} overlaps with ${if (isRemand) "remand" else "sentence"} of range $it")
          }
        } else if (it.end.isAfter(totalRange!!.end)) {
          totalRange = LocalDateRange.of(totalRange!!.start, it.end)
        }
        previousRangeIsRemand = isRemand
        previousRange = it
      }
    }
  }

  private fun readjustDates(it: ExtractableSentence) {
    sentenceCalculationService.calculateDatesFromAdjustments(it)
    log.info(it.buildString())
  }

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}
