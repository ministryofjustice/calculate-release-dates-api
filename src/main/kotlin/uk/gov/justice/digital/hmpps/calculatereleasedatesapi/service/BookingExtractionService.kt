package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.threeten.extra.LocalDateRange
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.ARD
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.CRD
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.ESED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.HDCED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.LED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.NCRD
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.NPD
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.SED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.SLED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.TUSED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.BookingTimelineGapException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.NoSentencesProvidedException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.BookingCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ExtractableSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.isBeforeOrEqualTo
import java.time.LocalDate
import java.time.Period
import java.time.temporal.ChronoUnit
import kotlin.math.min

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

    if (sentenceCalculation.homeDetentionCurfewEligibilityDate != null) {
      bookingCalculation.dates[HDCED] = sentenceCalculation.homeDetentionCurfewEligibilityDate!!
    }

    if (sentenceCalculation.notionalConditionalReleaseDate != null) {
      bookingCalculation.dates[NCRD] = sentenceCalculation.notionalConditionalReleaseDate!!
    }

    bookingCalculation.dates[ESED] = sentenceCalculation.unadjustedExpiryDate
    bookingCalculation.effectiveSentenceLength =
      getEffectiveSentenceLength(sentence.sentencedAt, sentenceCalculation.unadjustedExpiryDate)

    return bookingCalculation
  }

  private fun extractMultiple(booking: Booking): BookingCalculation {
    val bookingCalculation = walkTimelineOfBooking(booking)
    val earliestSentenceDate = booking.getAllExtractableSentences().minOf { it.sentencedAt }

    val mostRecentSentenceByReleaseDate = extractionService.mostRecentSentence(
      booking.getAllExtractableSentences(), SentenceCalculation::releaseDate
    )
    val latestReleaseDate: LocalDate = mostRecentSentenceByReleaseDate.sentenceCalculation.releaseDate!!

    val latestExpiryDate: LocalDate = extractionService.mostRecent(
      booking.getAllExtractableSentences(), SentenceCalculation::expiryDate
    )

    val latestUnadjustedExpiryDate: LocalDate = extractionService.mostRecent(
      booking.getAllExtractableSentences(), SentenceCalculation::unadjustedExpiryDate
    )

    val latestLicenseExpiryDate: LocalDate? = extractionService.mostRecentOrNull(
      booking.getAllExtractableSentences(), SentenceCalculation::licenceExpiryDate
    )

    val latestNonParoleDate: LocalDate? = extractManyNonParoleDate(booking, latestReleaseDate)

    val latestHomeDetentionCurfewEligibilityDate: LocalDate? = extractManyHomeDetentionCurfewEligibilityDate(
      earliestSentenceDate,
      latestUnadjustedExpiryDate,
      mostRecentSentenceByReleaseDate
    )

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

    if (latestHomeDetentionCurfewEligibilityDate != null) {
      bookingCalculation.dates[HDCED] = latestHomeDetentionCurfewEligibilityDate
    }

    if (latestNotionalConditionalReleaseDate != null) {
      bookingCalculation.dates[NCRD] = latestNotionalConditionalReleaseDate
    }

    bookingCalculation.dates[ESED] = latestUnadjustedExpiryDate
    bookingCalculation.effectiveSentenceLength = getEffectiveSentenceLength(
      earliestSentenceDate,
      latestUnadjustedExpiryDate
    )

    return bookingCalculation
  }

  private fun extractManyHomeDetentionCurfewEligibilityDate(earliestSentenceDate: LocalDate, latestUnadjustedExpiryDate: LocalDate, mostRecentSentenceByReleaseDate: ExtractableSentence): LocalDate? {
    val fourYearSentence = earliestSentenceDate.plusYears(FOUR)
    if (latestUnadjustedExpiryDate.isBefore(fourYearSentence)) {
      return mostRecentSentenceByReleaseDate.sentenceCalculation.homeDetentionCurfewEligibilityDate
    }
    return null
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
        //TODO tagged bail, UAL?
        //There is gap.

        // 1. Check if there have been any ADAs served
        var daysBetween = ChronoUnit.DAYS.between(workingRange.end, it.sentencedAt)
        val daysAdaServed = min(daysBetween - 1, totalAda.toLong())
        daysAwardedServed += daysAdaServed.toInt()
        workingRange = LocalDateRange.of(workingRange.start, previousSentence.sentenceCalculation.adjustedReleaseDate)

        daysBetween = ChronoUnit.DAYS.between(workingRange.end, it.sentencedAt)

        if (daysBetween > 0) {
          //There is still a gap

          //2. A release date has occurred but there are more sentences on the booking, therefore previous adjustments
          // should be wiped. TODO only remand?
          daysRemandNoLongerApplicable += booking.getOrZero(
            AdjustmentType.REMAND,
            previousSentence.sentenceCalculation.adjustedReleaseDate
          )

          //3. A release date has occurred but there are more sentences on the booking, therefore that gap should
          // be filled by remand.
          val daysRemand = booking.getOrZero(AdjustmentType.REMAND, it.sentencedAt) - daysRemandNoLongerApplicable
          if (workingRange.end.plusDays(daysRemand.toLong()).isBeforeOrEqualTo(it.sentencedAt.minusDays(1))) {
            throw BookingTimelineGapException("There is a gap between sentences $previousSentence AND $it")
          } else {
            workingRange = LocalDateRange.of(workingRange.start, itRange.end)
          }
        }
      }
      previousSentence = it
      readjustDates(it, daysAwardedServed, daysRemandNoLongerApplicable)
    }


    return bookingCalculation
  }

  private fun readjustDates(it: ExtractableSentence, daysAwardedServed: Int, daysRemandNoLongerApplicable: Int) {
    it.sentenceCalculation.calculatedTotalAwardedDays = it.sentenceCalculation.calculatedTotalAwardedDays - daysAwardedServed
    it.sentenceCalculation.calculatedTotalDeductedDays = it.sentenceCalculation.calculatedTotalDeductedDays - daysRemandNoLongerApplicable
  }

  private fun getEffectiveSentenceLength(start: LocalDate, end: LocalDate): Period =
    Period.between(start, end.plusDays(1))

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
    private const val FOUR = 4L
  }
}
