package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.CantExtractMultipleSentencesException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.NoSentencesProvidedException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.BookingCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceCalculation
import java.time.LocalDate

@Service
class BookingExtractionService(
  val extractionService: SentencesExtractionService
) {

  fun extract(
    booking: Booking
  ): BookingCalculation {
    return when (booking.sentences.size) {
      0 -> throw NoSentencesProvidedException("At least one sentence must be provided")
      1 -> extractSingle(booking)
      else -> {
        extractMultiple(booking)
      }
    }
  }

  private fun extractSingle(booking: Booking): BookingCalculation {
    val bookingCalculation = BookingCalculation()
    val sentence = booking.sentences[0]
    val sentenceCalculation = sentence.sentenceCalculation

    if (sentence.sentenceTypes.contains(SentenceType.SLED)) {
      bookingCalculation.dates[SentenceType.SLED] = sentenceCalculation.expiryDate!!
    } else {
      bookingCalculation.dates[SentenceType.SED] = sentenceCalculation.expiryDate!!
    }

    bookingCalculation.dates[sentence.getReleaseDateType()] = sentenceCalculation.releaseDate!!

    if (sentenceCalculation.licenceExpiryDate != null &&
      sentenceCalculation.licenceExpiryDate != sentenceCalculation.expiryDate
    ) {
      bookingCalculation.dates[SentenceType.LED] = sentenceCalculation.licenceExpiryDate!!
    }

    if (sentenceCalculation.nonParoleDate != null) {
      bookingCalculation.dates[SentenceType.NPD] = sentenceCalculation.nonParoleDate!!
    }

    if (sentenceCalculation.topUpSupervisionDate != null) {
      bookingCalculation.dates[SentenceType.TUSED] = sentenceCalculation.topUpSupervisionDate!!
    }

    if (sentenceCalculation.homeDetentionCurfewExpiryDateDate != null) {
      bookingCalculation.dates[SentenceType.HDCED] = sentenceCalculation.homeDetentionCurfewExpiryDateDate!!
    }

    return bookingCalculation
  }

  private fun extractMultiple(booking: Booking): BookingCalculation {
    if (
      extractionService.hasNoConsecutiveSentences(booking.sentences.stream()) &&
      extractionService.allOverlap(booking.sentences)
    ) {

      val mostRecentByReleaseDateSentence = extractionService.mostRecentSentence(
        booking.sentences, SentenceCalculation::releaseDate
      )
      val latestReleaseDate: LocalDate = mostRecentByReleaseDateSentence.sentenceCalculation.releaseDate!!

      val latestExpiryDate: LocalDate = extractionService.mostRecent(
        booking.sentences, SentenceCalculation::expiryDate
      )

      val latestLicenseExpiryDate: LocalDate = extractionService.mostRecent(
        booking.sentences, SentenceCalculation::licenceExpiryDate
      )

      val latestNonParoleDate: LocalDate? = extractionService.mostRecentOrNull(
        booking.sentences, SentenceCalculation::nonParoleDate
      )

      val latestHomeDetentionCurfewExpiryDateDate: LocalDate? =
        mostRecentByReleaseDateSentence.sentenceCalculation.homeDetentionCurfewExpiryDateDate

      val effectiveTopUpSupervisionDate = extractManyTopUpSuperVisionDate(booking, latestLicenseExpiryDate)

      val isReleaseDateConditional = extractManyIsReleaseConditional(
        booking, latestReleaseDate, latestExpiryDate, latestLicenseExpiryDate
      )

      val bookingCalculation = BookingCalculation()
      if (latestExpiryDate == latestLicenseExpiryDate) {
        bookingCalculation.dates[SentenceType.SLED] = latestExpiryDate
      } else {
        bookingCalculation.dates[SentenceType.SED] = latestExpiryDate
        bookingCalculation.dates[SentenceType.LED] = latestLicenseExpiryDate
      }

      if (isReleaseDateConditional) {
        bookingCalculation.dates[SentenceType.CRD] = latestReleaseDate
      } else {
        bookingCalculation.dates[SentenceType.ARD] = latestReleaseDate
      }

      if (latestNonParoleDate != null) {
        bookingCalculation.dates[SentenceType.NPD] = latestNonParoleDate
      }

      if (effectiveTopUpSupervisionDate != null) {
        bookingCalculation.dates[SentenceType.TUSED] = effectiveTopUpSupervisionDate
      }

      if (latestHomeDetentionCurfewExpiryDateDate != null) {
        bookingCalculation.dates[SentenceType.HDCED] = latestHomeDetentionCurfewExpiryDateDate
      }
      return bookingCalculation
    } else {
      throw CantExtractMultipleSentencesException("Can't extract a single date from multiple sentences")
    }
  }

  private fun extractManyTopUpSuperVisionDate(booking: Booking, latestLicenseExpiryDate: LocalDate): LocalDate? {
    val latestTopUpSupervisionDate: LocalDate? = extractionService.mostRecentOrNull(
      booking.sentences, SentenceCalculation::topUpSupervisionDate
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
      booking.sentences, latestReleaseDate
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
