package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceIdentificationTrack.SDS_BEFORE_CJA_LASPO
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.NoSentencesProvidedException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ConsecutiveSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Sentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SingleTermSentence

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
      log.info(sentence.buildString())
    }
    return booking
  }

  fun walkTimelineOfSentence(booking: Booking): Booking {
    // TODO Check with analysis on this. if we dont exclude schedule 15's example 37 fails.
    //  Schedule 15's are out of scope at the moment - so one for the future
//    if (booking.sentences.any { it.offence.isScheduleFifteen }) return booking

//
//
//    var previousReleaseDateMinusDaysAwarded: LocalDate? = null
//    for (sentence: ExtractableSentence in booking.getAllExtractableSentences().sortedWith(compareBy<ExtractableSentence> {it.sentencedAt }.thenByDescending { it.getLengthInDays() })) {
//      if (previousReleaseDateMinusDaysAwarded == null) {
//        previousReleaseDateMinusDaysAwarded =
//          sentence.sentenceCalculation.unadjustedReleaseDate.minusDays(sentence.sentenceCalculation.calculatedTotalDeductedDays.toLong())
//        continue
//      }
//      if (sentence.sentencedAt.isAfter(previousReleaseDateMinusDaysAwarded)) {
//        val additionalDaysAlreadyServed =
//          ChronoUnit.DAYS.between(previousReleaseDateMinusDaysAwarded, sentence.sentencedAt) - 1
//        log.info("Adjusting release date for days already served. Adjusting by $additionalDaysAlreadyServed")
//        sentence.sentenceCalculation.releaseDate =
//          sentence.sentenceCalculation.releaseDate!!.minusDays(additionalDaysAlreadyServed)
//      }
//
//      previousReleaseDateMinusDaysAwarded =
//        sentence.sentenceCalculation.unadjustedReleaseDate.minusDays(sentence.sentenceCalculation.calculatedTotalDeductedDays.toLong())
//    }

    return booking
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

  fun createSingleTermSentences(booking: Booking): Booking {
    if (booking.sentences.size > 1 &&
      booking.sentences.all { it.identificationTrack == SDS_BEFORE_CJA_LASPO && it.consecutiveSentenceUUIDs.isEmpty() }
    ) {
      booking.singleTermSentence = SingleTermSentence(booking.sentences)
      sentenceIdentificationService.identify(booking.singleTermSentence!!, booking.offender)
      sentenceCalculationService.calculate(booking.singleTermSentence!!, booking)
      log.info(booking.singleTermSentence!!.buildString())
    }
    return booking
  }

  fun createConsecutiveSentences(booking: Booking): Booking {
    val sentencesConsecutiveTo = booking.sentences.filter { it.consecutiveSentenceUUIDs.isNotEmpty() }

    val sentenceChains: MutableList<MutableList<Sentence>> = mutableListOf()

    sentencesConsecutiveTo.forEach { consecutive ->
      val first = booking.sentences.find { it.identifier == consecutive.consecutiveSentenceUUIDs[0] }!!

      val existingChain: MutableList<Sentence>? = sentenceChains.find { it.contains(first) }

      if (existingChain != null) {
        existingChain.add(existingChain.indexOf(first) + 1, consecutive)
      } else {
        sentenceChains.add(mutableListOf(first, consecutive))
      }
    }

    booking.consecutiveSentences = sentenceChains.map { ConsecutiveSentence(it) }

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
