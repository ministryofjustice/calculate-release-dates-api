package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceIdentificationTrack.SDS_BEFORE_CJA_LASPO
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ExtendedDeterminateSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ExtendedStandardConsecutiveSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.StandardConsecutiveSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SingleTermSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.StandardSentence

@Service
class BookingCalculationService(
  val sentenceCalculationService: SentenceCalculationService,
  val sentenceIdentificationService: SentenceIdentificationService
) {

  fun identify(booking: Booking): Booking {
    for (sentence in booking.sentences) {
      sentenceIdentificationService.identify(sentence, booking.offender)
    }
    return booking
  }

  fun calculate(booking: Booking): Booking {
    for (sentence in booking.sentences) {
      sentenceCalculationService.calculate(sentence, booking)
    }
    return booking
  }

  fun createSingleTermSentences(booking: Booking): Booking {
    if (booking.sentences.size > 1 &&
      booking.sentences.all { it.identificationTrack == SDS_BEFORE_CJA_LASPO && it.consecutiveSentenceUUIDs.isEmpty() } &&
      booking.sentences.minOf { it.sentencedAt } != booking.sentences.maxOf { it.sentencedAt } &&
      booking.sentences.all { !it.isRecall() }
    ) {
      booking.singleTermSentence = SingleTermSentence(booking.sentences.map { it as StandardSentence})
      sentenceIdentificationService.identify(booking.singleTermSentence!!, booking.offender)
      sentenceCalculationService.calculate(booking.singleTermSentence!!, booking)
      log.info(booking.singleTermSentence!!.buildString())
    }
    return booking
  }

  fun createConsecutiveSentences(booking: Booking): Booking {
    val (baseSentences, consecutiveSentences) = booking.sentences.partition { it.consecutiveSentenceUUIDs.isEmpty() }
    val sentencesByPrevious = consecutiveSentences.associateBy { it.consecutiveSentenceUUIDs.first() }

    booking.consecutiveSentences = baseSentences
      .map { baseSentence -> generateSequence(baseSentence) { sentencesByPrevious[it.identifier] }.toList() }
      .filter { it.size > 1 }
      .map { it ->
        if (it[0] is StandardSentence) {
          StandardConsecutiveSentence(it.map { it as StandardSentence })
        } else {
          ExtendedStandardConsecutiveSentence(it.map { it as ExtendedDeterminateSentence })
        }
      }

    booking.consecutiveSentences.forEach {
      sentenceIdentificationService.identify(it, booking.offender)
      sentenceCalculationService.calculate(it, booking)
      log.info(it.buildString())
    }
    return booking
  }

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}
