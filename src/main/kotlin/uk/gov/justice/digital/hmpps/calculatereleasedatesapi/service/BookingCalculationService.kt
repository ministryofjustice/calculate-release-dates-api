package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.CantExtractMultipleSentencesException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.NoSentencesProvidedException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.BookingCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceType
import java.time.LocalDate

@Service
class BookingCalculationService(
  val sentenceCalculationService: SentenceCalculationService,
  val sentenceIdentificationService: SentenceIdentificationService,
  val extractionService: SentencesExtractionService,
  val combinationService: SentenceCombinationService
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

  fun combine(booking: Booking): Booking {
    return when (booking.sentences.size) {
      0 -> throw NoSentencesProvidedException("At least one sentence must be provided")
      1 -> booking
      else -> {
        val workingBooking = combinationService.combineConsecutiveSentences(booking)
        workingBooking.sentences.forEach { sentence ->
          if (!sentence.isSentenceCalculated()) {
            sentenceCalculationService.calculate(sentence, booking)
            log.info(sentence.buildString())
          }
        }
        return workingBooking
      }
    }
  }

  private fun extractSingle(booking: Booking): BookingCalculation {
    val sentence = booking.sentences[0]
    val sentenceCalculation = sentence.sentenceCalculation
    if (sentence.sentenceTypes.contains(SentenceType.SLED)) {
      return BookingCalculation(
        null,
        sentenceCalculation.expiryDate,
        sentenceCalculation.releaseDate,
        sentenceCalculation.topUpSupervisionDate,
        sentenceCalculation.isReleaseDateConditional
      )
    } else {
      return BookingCalculation(
        sentenceCalculation.licenceExpiryDate,
        sentenceCalculation.expiryDate,
        sentenceCalculation.releaseDate,
        sentenceCalculation.topUpSupervisionDate,
        sentenceCalculation.isReleaseDateConditional
      )
    }
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
      var latestLicenseExpiryDate: LocalDate? = extractionService.mostRecent(
        booking.sentences, SentenceCalculation::licenceExpiryDate
      )

      if (latestLicenseExpiryDate != null &&
        (
          latestLicenseExpiryDate.isEqual(latestReleaseDate) ||
            latestLicenseExpiryDate.isEqual(latestExpiryDate)
          )
      ) {
        latestLicenseExpiryDate = null
      } else {
        // PSI Example 16 Release is therefore on license which means the release date is a CRD
        isReleaseDateConditional = true
      }

      return BookingCalculation(
        latestLicenseExpiryDate,
        latestExpiryDate,
        latestReleaseDate,
        extractionService.mostRecent(booking.sentences, SentenceCalculation::topUpSupervisionDate),
        isReleaseDateConditional
      )
    } else {
      throw CantExtractMultipleSentencesException("Can't extract a single date from multiple sentences")
    }
  }

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}
