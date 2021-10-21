package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.NoSentencesProvidedException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking

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
