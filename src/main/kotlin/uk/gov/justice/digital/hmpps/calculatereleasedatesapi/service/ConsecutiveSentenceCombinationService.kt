package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.CannotMergeSentencesException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Sentence

@Service
class ConsecutiveSentenceCombinationService(val sentenceCombinationService: SentenceCombinationService) {

  fun combineConsecutiveSentences(booking: Booking): Booking {
    val workingBooking: Booking = booking.copy()
    // find a list of sentences that have consecutive sentences
    val consecutiveSentences = booking.sentences.filter { it.consecutiveSentences.isNotEmpty() }
    for (sentence in consecutiveSentences) {
      combineSentenceWithEachOfItsConsecutiveSentences(sentence, workingBooking)
    }
    return workingBooking
  }

  private fun combineSentenceWithEachOfItsConsecutiveSentences(
    sentence: Sentence,
    workingBooking: Booking
  ) {
    val consecutiveSentences = sentence.consecutiveSentences.toList()
    for (consecutiveSentence in consecutiveSentences) {
      if (sentence != consecutiveSentence) {
        sentenceCombinationService.combineTwoSentences(
          sentence,
          consecutiveSentence,
          workingBooking,
          this::mergeConsecutiveSentences
        )
      }
    }
  }

  fun mergeConsecutiveSentences(firstSentence: Sentence, secondSentence: Sentence): Sentence {
    if (!firstSentence.canMergeConsecutivelyWith(secondSentence)) {
      throw CannotMergeSentencesException("Incompatible sentence types")
    }

    return Sentence(
      sentenceCombinationService.earliestOffence(firstSentence, secondSentence),
      sentenceCombinationService.combinedDuration(firstSentence, secondSentence),
      sentenceCombinationService.earliestSentencedAt(firstSentence, secondSentence),
    )
  }
}
