package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
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
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.NoSentencesProvidedException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.BookingCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ExtractableSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ReleaseDateCalculationBreakdown
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceCalculation
import java.time.LocalDate
import java.time.Period

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
    bookingCalculation.breakdownByReleaseDateType = sentenceCalculation.breakdownByReleaseDateType

    return bookingCalculation
  }

  private fun extractMultiple(booking: Booking): BookingCalculation {
    val breakdownByReleaseDateType: MutableMap<ReleaseDateType, ReleaseDateCalculationBreakdown> = mutableMapOf()
    val bookingCalculation = BookingCalculation()
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

    val latestHDCEDAndBreakdown =
      extractManyHomeDetentionCurfewEligibilityDate(
        earliestSentenceDate,
        latestUnadjustedExpiryDate,
        mostRecentSentenceByReleaseDate
      )

    val latestTUSEDAndBreakdown = if (latestLicenseExpiryDate != null) {
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

    if (latestTUSEDAndBreakdown != null) {
      bookingCalculation.dates[TUSED] = latestTUSEDAndBreakdown.first
      breakdownByReleaseDateType[TUSED] = latestTUSEDAndBreakdown.second
    }

    if (latestHDCEDAndBreakdown != null) {
      bookingCalculation.dates[HDCED] = latestHDCEDAndBreakdown.first
      breakdownByReleaseDateType[HDCED] = latestHDCEDAndBreakdown.second
    }

    if (latestNotionalConditionalReleaseDate != null) {
      bookingCalculation.dates[NCRD] = latestNotionalConditionalReleaseDate
    }

    bookingCalculation.dates[ESED] = latestUnadjustedExpiryDate
    bookingCalculation.effectiveSentenceLength = getEffectiveSentenceLength(
      earliestSentenceDate,
      latestUnadjustedExpiryDate
    )

    bookingCalculation.breakdownByReleaseDateType = breakdownByReleaseDateType
    return bookingCalculation
  }

  private fun extractManyHomeDetentionCurfewEligibilityDate(
    earliestSentenceDate: LocalDate,
    latestUnadjustedExpiryDate: LocalDate,
    mostRecentSentenceByReleaseDate: ExtractableSentence
  ): Pair<LocalDate, ReleaseDateCalculationBreakdown>? {
    val fourYearSentence = earliestSentenceDate.plusYears(FOUR)

    return if (latestUnadjustedExpiryDate.isBefore(fourYearSentence)) {
      mostRecentSentenceByReleaseDate.sentenceCalculation.homeDetentionCurfewEligibilityDate!! to mostRecentSentenceByReleaseDate.sentenceCalculation.breakdownByReleaseDateType[HDCED]!!
    } else
      null
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
      latestReleaseDate
    } else {
      null
    }
  }

  private fun extractManyTopUpSuperVisionDate(
    booking: Booking,
    latestLicenseExpiryDate: LocalDate
  ): Pair<LocalDate, ReleaseDateCalculationBreakdown>? {
    val latestTUSEDSentence = booking.getAllExtractableSentences()
      .filter { it.sentenceCalculation.topUpSupervisionDate != null }
      .maxByOrNull { it.sentenceCalculation.topUpSupervisionDate!! }

    return if (latestTUSEDSentence != null && latestTUSEDSentence.sentenceCalculation.topUpSupervisionDate!!.isAfter(
        latestLicenseExpiryDate
      )
    ) {
      latestTUSEDSentence.sentenceCalculation.topUpSupervisionDate!! to latestTUSEDSentence.sentenceCalculation.breakdownByReleaseDateType[TUSED]!!
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
