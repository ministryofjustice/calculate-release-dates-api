package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.NoSentencesProvidedException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Sentence
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Service
class BookingCalculationService(
  val sentenceCalculationService: SentenceCalculationService,
  val sentenceIdentificationService: SentenceIdentificationService,
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

  fun combineConsecutive(booking: Booking): Booking {
    return applyMultiple(booking, consecutiveSentenceCombinationService::combineConsecutiveSentences)
  }

  fun combineConcurrent(booking: Booking): Booking {
    return applyMultiple(booking, concurrentSentenceCombinationService::combineConcurrentSentences)
  }

  //  TODO This doesnt sit well with the wider pattern of performing a calculation after each major step. So some tech
  //   debt here but will be refactored once we consider ADA's more and decide whether we pull back adjustments at a
  //   more granular level
  fun adjustForAdditionalDaysAlreadyServed(booking: Booking): Booking {
    // TODO Check with analysis on this. if we dont exclude schedule 15's example 37 fails.
    //  Schedule 15's are out of scope at the moment - so one for the future
    if (booking.sentences.any { it.offence.isScheduleFifteen }) return booking

    val workingBooking: Booking = booking.copy()
    var previousReleaseDateMinusDaysAwarded: LocalDate? = null

    for (sentence: Sentence in workingBooking.sentences.sortedBy { it.sentencedAt }) {
      if (previousReleaseDateMinusDaysAwarded == null) {
        previousReleaseDateMinusDaysAwarded =
          sentence.sentenceCalculation.unadjustedReleaseDate.minusDays(sentence.sentenceCalculation.calculatedTotalDeductedDays.toLong())
        continue
      }
      if (sentence.sentencedAt.isAfter(previousReleaseDateMinusDaysAwarded)) {
        val additionalDaysAlreadyServed =
          ChronoUnit.DAYS.between(previousReleaseDateMinusDaysAwarded, sentence.sentencedAt) - 1
        sentence.sentenceCalculation.releaseDate =
          sentence.sentenceCalculation.releaseDate!!.minusDays(additionalDaysAlreadyServed)
      }

      previousReleaseDateMinusDaysAwarded =
        sentence.sentenceCalculation.unadjustedReleaseDate.minusDays(sentence.sentenceCalculation.calculatedTotalDeductedDays.toLong())
    }

    return workingBooking
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

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}
