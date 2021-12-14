package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.threeten.extra.LocalDateRange
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.ARD
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.CRD
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.HDCED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.LED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.NCRD
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.NPD
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.SED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.SLED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.TUSED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.NoSentencesProvidedException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.BookingTimelineGapException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.BookingCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceCalculation
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Service
class BookingExtractionService(
  val extractionService: SentencesExtractionService
) {

  fun extract(
    booking: Booking
  ): BookingCalculation {
    return when (booking.getAllExtractableSentences().size) {
      0 -> throw NoSentencesProvidedException("At least one sentence must be provided")
      1 -> extractSingle(booking)
      else -> {
        extractMultiple(booking)
      }
    }
  }

  private fun extractSingle(booking: Booking): BookingCalculation {
    val bookingCalculation = BookingCalculation()
    val sentence = booking.getAllExtractableSentences()[0]
    val sentenceCalculation = sentence.sentenceCalculation

    if (sentence.releaseDateTypes.contains(SLED)) {
      bookingCalculation.dates[SLED] = sentenceCalculation.expiryDate!!
    } else {
      bookingCalculation.dates[SED] = sentenceCalculation.expiryDate!!
    }

    bookingCalculation.dates[sentence.getReleaseDateType()] = sentenceCalculation.releaseDate!!

    if (sentenceCalculation.licenceExpiryDate != null &&
      sentenceCalculation.licenceExpiryDate != sentenceCalculation.expiryDate
    ) {
      bookingCalculation.dates[LED] = sentenceCalculation.licenceExpiryDate!!
    }

    if (sentenceCalculation.nonParoleDate != null) {
      bookingCalculation.dates[NPD] = sentenceCalculation.nonParoleDate!!
    }

    if (sentenceCalculation.topUpSupervisionDate != null) {
      bookingCalculation.dates[TUSED] = sentenceCalculation.topUpSupervisionDate!!
    }

    if (sentenceCalculation.homeDetentionCurfewExpiryDateDate != null) {
      bookingCalculation.dates[HDCED] = sentenceCalculation.homeDetentionCurfewExpiryDateDate!!
    }

    if (sentenceCalculation.notionalConditionalReleaseDate != null) {
      bookingCalculation.dates[NCRD] = sentenceCalculation.notionalConditionalReleaseDate!!
    }

    return bookingCalculation
  }

  private fun extractMultiple(booking: Booking): BookingCalculation {
    val bookingCalculation = walkTimelineOfBooking(booking)

    val mostRecentSentenceByReleaseDate = extractionService.mostRecentSentence(
      booking.getAllExtractableSentences(), SentenceCalculation::releaseDate
    )
    val latestReleaseDate: LocalDate = mostRecentSentenceByReleaseDate.sentenceCalculation.releaseDate!!

    val latestExpiryDate: LocalDate = extractionService.mostRecent(
      booking.getAllExtractableSentences(), SentenceCalculation::expiryDate
    )

    val latestLicenseExpiryDate: LocalDate? = extractionService.mostRecentOrNull(
      booking.getAllExtractableSentences(), SentenceCalculation::licenceExpiryDate
    )

    val latestNonParoleDate: LocalDate? = extractManyNonParoleDate(booking, latestReleaseDate)

    val latestHomeDetentionCurfewExpiryDateDate: LocalDate? =
      mostRecentSentenceByReleaseDate.sentenceCalculation.homeDetentionCurfewExpiryDateDate

    val effectiveTopUpSupervisionDate = if (latestLicenseExpiryDate != null) {
      extractManyTopUpSuperVisionDate(booking, latestLicenseExpiryDate)
    } else {
      null
    }

    val isReleaseDateConditional = extractManyIsReleaseConditional(
      booking, latestReleaseDate, latestExpiryDate, latestLicenseExpiryDate
    )

    val latestNotionalConditionalReleaseDate: LocalDate? = extractionService.mostRecentOrNull(
      booking.getAllExtractableSentences(), SentenceCalculation::notionalConditionalReleaseDate
    )

    if (latestExpiryDate == latestLicenseExpiryDate) {
      bookingCalculation.dates[SLED] = latestExpiryDate
    } else {
      bookingCalculation.dates[SED] = latestExpiryDate
      if (latestLicenseExpiryDate != null) {
        bookingCalculation.dates[LED] = latestLicenseExpiryDate
      }
    }

    if (isReleaseDateConditional) {
      bookingCalculation.dates[CRD] = latestReleaseDate.minusDays(bookingCalculation.daysAwardedServed)
    } else {
      bookingCalculation.dates[ARD] = latestReleaseDate.minusDays(bookingCalculation.daysAwardedServed)
    }

    if (latestNonParoleDate != null) {
      bookingCalculation.dates[NPD] = latestNonParoleDate
    }

    if (effectiveTopUpSupervisionDate != null) {
      bookingCalculation.dates[TUSED] = effectiveTopUpSupervisionDate
    }

    if (latestHomeDetentionCurfewExpiryDateDate != null) {
      bookingCalculation.dates[HDCED] = latestHomeDetentionCurfewExpiryDateDate
    }

    if (latestNotionalConditionalReleaseDate != null) {
      bookingCalculation.dates[NCRD] = latestNotionalConditionalReleaseDate
    }

    return bookingCalculation
  }

  /*
     This method walks through the timeline of all the booking, checking for gaps between the release date and the
     next sentence date of all the extractable sentences.
     If there is a gap, it must be filled by any of:
     1. Served ADAs,
     2. Remand, i.e. A prisoner is kept on remand for an upcoming court date (TODO)
     3. A license recall (TODO)
    */
  private fun walkTimelineOfBooking(booking: Booking): BookingCalculation {
    val bookingCalculation = BookingCalculation()
    var daysAwardedServed = 0L
    // TODO Check with analysis on this. if we dont exclude schedule 15's example 37 fails.
    //  Schedule 15's are out of scope at the moment - so one for the future
    // TODO LH: I think the schedule 15 is because we use PED as the release date.
    //  But that is only an eligible to be released date, not the date they're actually released on
    if (booking.sentences.any { it.offence.isScheduleFifteen }) return bookingCalculation
    val sortedSentences = booking.getAllExtractableSentences().sortedBy { it.sentencedAt }
    var workingRange = sortedSentences[0].getDateRangeFromStartToReleaseWithoutDaysAwarded()
    val totalAda = booking.getAllExtractableSentences()[0].sentenceCalculation.calculatedTotalAwardedDays.toLong()
    var previousSentence = sortedSentences[0]
    sortedSentences.forEach {
      val sentenceRange = it.getDateRangeFromStartToReleaseWithoutDaysAwarded()
      if (workingRange.isConnected(sentenceRange)) {
        if (sentenceRange.end.isAfter(workingRange.end)) {
          workingRange = LocalDateRange.of(workingRange.start, sentenceRange.end)
        }
      } else {
        daysAwardedServed += ChronoUnit.DAYS.between(workingRange.end, it.sentencedAt) - 1
        workingRange = LocalDateRange.of(workingRange.start, sentenceRange.end)
        if (totalAda < daysAwardedServed) {
          throw BookingTimelineGapException("There is a gap between sentences $previousSentence AND $it")
        }
      }
      previousSentence = it
    }

    if (daysAwardedServed != 0L) {
      BookingCalculationService.log.info("Adjusting release date for days already served. Adjusting by $daysAwardedServed")
      bookingCalculation.daysAwardedServed = daysAwardedServed
    }

    return bookingCalculation
  }

  private fun extractManyNonParoleDate(booking: Booking, latestReleaseDate: LocalDate): LocalDate? {

    val mostRecentNonParoleDate = extractionService.mostRecentOrNull(
      booking.getAllExtractableSentences(), SentenceCalculation::nonParoleDate
    )
    return if (mostRecentNonParoleDate != null &&
      mostRecentNonParoleDate.isAfter(latestReleaseDate)
    ) {
      return latestReleaseDate
    } else {
      null
    }
  }

  private fun extractManyTopUpSuperVisionDate(booking: Booking, latestLicenseExpiryDate: LocalDate): LocalDate? {
    val latestTopUpSupervisionDate: LocalDate? = extractionService.mostRecentOrNull(
      booking.getAllExtractableSentences(), SentenceCalculation::topUpSupervisionDate
    )

    return if (latestTopUpSupervisionDate != null &&
      latestTopUpSupervisionDate.isAfter(latestLicenseExpiryDate)
    ) {
      latestTopUpSupervisionDate
    } else {
      null
    }
  }

  private fun extractManyIsReleaseConditional(
    booking: Booking,
    latestReleaseDate: LocalDate?,
    latestExpiryDate: LocalDate?,
    latestLicenseExpiryDate: LocalDate?
  ): Boolean {
    var isReleaseDateConditional = extractionService.getAssociatedReleaseType(
      booking.getAllExtractableSentences(), latestReleaseDate
    )
    if (
      (latestLicenseExpiryDate != null) &&
      !(
        latestLicenseExpiryDate.isEqual(latestReleaseDate) ||
          latestLicenseExpiryDate.isEqual(latestExpiryDate)
        )

    ) {
      // PSI Example 16 Release is therefore on license which means the release date is a CRD
      isReleaseDateConditional = true
    }
    return isReleaseDateConditional
  }

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}
