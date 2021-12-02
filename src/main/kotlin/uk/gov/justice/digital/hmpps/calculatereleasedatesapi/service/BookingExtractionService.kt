package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.ARD
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.CRD
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.HDCED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.LED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.NCRD
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.NPD
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.SED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.SLED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.TUSED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.CantExtractMultipleSentencesException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.NoSentencesProvidedException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.BookingCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculableSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ExtractableSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceCalculation
import java.time.LocalDate

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
    if (
      extractionService.allOverlap(booking.getAllExtractableSentences())
    ) {

      val mostRecentSentenceByReleaseDate = extractionService.mostRecentSentence(
        booking.getAllExtractableSentences(), SentenceCalculation::releaseDate
      )
      val latestReleaseDate: LocalDate = mostRecentSentenceByReleaseDate.sentenceCalculation.releaseDate!!

      val latestExpiryDate: LocalDate = extractionService.mostRecent(
        booking.getAllExtractableSentences(), SentenceCalculation::expiryDate
      )

      val latestLicenseExpiryDate: LocalDate = extractionService.mostRecent(
        booking.getAllExtractableSentences(), SentenceCalculation::licenceExpiryDate
      )

      val latestNonParoleDate: LocalDate? = extractManyNonParoleDate(booking, latestReleaseDate)

      val latestHomeDetentionCurfewExpiryDateDate: LocalDate? =
        mostRecentSentenceByReleaseDate.sentenceCalculation.homeDetentionCurfewExpiryDateDate

      val effectiveTopUpSupervisionDate = extractManyTopUpSuperVisionDate(booking, latestLicenseExpiryDate)

      val isReleaseDateConditional = extractManyIsReleaseConditional(
        booking, latestReleaseDate, latestExpiryDate, latestLicenseExpiryDate
      )

      val latestNotionalConditionalReleaseDate: LocalDate? = extractionService.mostRecentOrNull(
        booking.getAllExtractableSentences(), SentenceCalculation::notionalConditionalReleaseDate
      )

      val bookingCalculation = BookingCalculation()
      if (latestExpiryDate == latestLicenseExpiryDate) {
        bookingCalculation.dates[SLED] = latestExpiryDate
      } else {
        bookingCalculation.dates[SED] = latestExpiryDate
        bookingCalculation.dates[LED] = latestLicenseExpiryDate
      }

      if (isReleaseDateConditional) {
        bookingCalculation.dates[CRD] = latestReleaseDate
      } else {
        bookingCalculation.dates[ARD] = latestReleaseDate
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
    } else {
      throw CantExtractMultipleSentencesException("Can't extract a single date from multiple sentences")
    }
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
    if (!(
      (latestLicenseExpiryDate != null) &&
        (
          latestLicenseExpiryDate.isEqual(latestReleaseDate) ||
            latestLicenseExpiryDate.isEqual(latestExpiryDate)
          )
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
