package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.CantExtractMultipleSentencesException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.NoSentencesProvidedException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.BookingCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Sentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.isAfterOrEqualTo
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.isBeforeOrEqualTo
import java.time.Duration
import java.time.LocalDate
import java.time.Period
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalUnit

@Service
class BookingCalculationService(
  val sentenceCalculationService: SentenceCalculationService,
  val sentenceIdentificationService: SentenceIdentificationService,
  val extractionService: SentencesExtractionService,
  val consecutiveSentenceCombinationService: ConsecutiveSentenceCombinationService,
  val concurrentSentenceCombinationService: ConcurrentSentenceCombinationService
) {

  fun identify(booking: Booking): Booking {
    for (sentence in booking.sentences) {
      sentenceIdentificationService.identify(sentence, booking.offender)
    }
    return booking
  }

  fun associateConsecutive(booking: Booking): Booking {
    for (sentence in booking.sentences) {
      sentence.associateSentences(booking.sentences)
    }
    return booking
  }

  fun calculate(booking: Booking): Booking {
    for (sentence in booking.sentences) {
      sentenceCalculationService.calculate(sentence, booking)
      log.info(sentence.buildString())
    }
    return booking
  }

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

  private fun applyMultiple(booking: Booking, function: (Booking) -> Booking): Booking {
    return when (booking.sentences.size) {
      0 -> throw NoSentencesProvidedException("At least one sentence must be provided")
      1 -> booking
      else -> {
        val workingBooking = function(booking)
        return calculate(workingBooking)
      }
    }
  }

  fun combineConsecutive(booking: Booking): Booking {
    return applyMultiple(booking, consecutiveSentenceCombinationService::combineConsecutiveSentences)
  }

  fun combineConcurrent(booking: Booking): Booking {
    return applyMultiple(booking, concurrentSentenceCombinationService::combineConcurrentSentences)
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

    return bookingCalculation
  }

  private fun extractMultiple(booking: Booking): BookingCalculation {
    if (
      extractionService.hasNoConsecutiveSentences(booking.sentences.stream()) &&
      extractionService.allOverlap(booking.sentences)
    ) {

      val latestReleaseDate: LocalDate? = extractionService.mostRecent(
        booking.sentences, SentenceCalculation::releaseDate
      )

      var isReleaseDateConditional = extractionService.getAssociatedReleaseType(
        booking.sentences, latestReleaseDate
      )

      val latestExpiryDate: LocalDate? = extractionService.mostRecent(
        booking.sentences, SentenceCalculation::expiryDate
      )

      val latestLicenseExpiryDate: LocalDate? = extractionService.mostRecent(
        booking.sentences, SentenceCalculation::licenceExpiryDate
      )

      val latestUnadjustedExpiryDate: LocalDate? = extractionService.mostRecent(
        booking.sentences, SentenceCalculation::unadjustedExpiryDate
      )

      val latestNonParoleDate: LocalDate? = extractionService.mostRecent(
        booking.sentences, SentenceCalculation::nonParoleDate
      )

      val earliestSentenceDate: LocalDate? = extractionService.earliestDate(
        booking.sentences, Sentence::sentencedAt
      )

      val latestTopUpSupervisionDate: LocalDate? =
        if (latestUnadjustedExpiryDate != null && earliestSentenceDate != null) {
          if (latestUnadjustedExpiryDate.isBefore(earliestSentenceDate.plusYears(TWO.toLong())) && latestUnadjustedExpiryDate.isAfterOrEqualTo(
              earliestSentenceDate.plusDays(TWO.toLong())
            )
          ) {
            extractionService.mostRecent(
              booking.sentences, SentenceCalculation::topUpSupervisionDate
            )
          } else {
            null
          }
        } else {
          null
        }


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

      val bookingCalculation = BookingCalculation()
      if (latestExpiryDate != null && latestExpiryDate == latestLicenseExpiryDate) {
        bookingCalculation.dates[SentenceType.SLED] = latestExpiryDate
      } else if (latestExpiryDate != null) {
        bookingCalculation.dates[SentenceType.SED] = latestExpiryDate
      }

      if (isReleaseDateConditional) {
        bookingCalculation.dates[SentenceType.CRD] = latestReleaseDate!!
      } else {
        bookingCalculation.dates[SentenceType.ARD] = latestReleaseDate!!
      }

      if (latestLicenseExpiryDate != null && latestExpiryDate != latestLicenseExpiryDate) {
        bookingCalculation.dates[SentenceType.LED] = latestLicenseExpiryDate
      }

      if (latestNonParoleDate != null) {
        bookingCalculation.dates[SentenceType.NPD] = latestNonParoleDate
      }

      if (latestTopUpSupervisionDate != null) {
        bookingCalculation.dates[SentenceType.TUSED] = latestTopUpSupervisionDate
      }
      return bookingCalculation
    } else {
      throw CantExtractMultipleSentencesException("Can't extract a single date from multiple sentences")
    }
  }

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
    const val TWO: Int = 2
  }
}
