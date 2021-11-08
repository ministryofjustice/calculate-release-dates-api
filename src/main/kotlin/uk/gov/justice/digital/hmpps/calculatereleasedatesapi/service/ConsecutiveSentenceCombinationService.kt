package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.CannotMergeSentencesException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.RecursionDepthExceededException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Sentence

@Service
class ConsecutiveSentenceCombinationService(val sentenceCombinationService: SentenceCombinationService) {

  fun combineConsecutiveSentences(booking: Booking): Booking {
    return combineConsecutiveSentencesRecursion(booking)
  }

  fun combineConsecutiveSentencesRecursion(booking: Booking, depth: Int = 0): Booking {
    if (depth >= MAX_DEPTH) {
      throw RecursionDepthExceededException(
        "Maximum depth reached in recursion trying to combine consecutive sentences"
      )
    }

    val workingBooking: Booking = booking.copy()
    // find a first sentences that has consecutive sentences
    val consecutiveSentence = workingBooking.sentences.find { it.consecutiveSentences.isNotEmpty() }
    if (consecutiveSentence != null) {
      combineSentenceWithEachOfItsConsecutiveSentences(consecutiveSentence, workingBooking)
    }
    val moreConsecutiveSentences = workingBooking.sentences.any { it.consecutiveSentences.isNotEmpty() }
    if (moreConsecutiveSentences) {
      // Merged consecutive sentences still have consecutive sentences.
      // i.e. there is a chain of consecutive sentences longer than 2.
      // Recursively call this function again.
      return combineConsecutiveSentencesRecursion(workingBooking, depth + 1)
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
      sentenceParts = firstSentence.sentenceParts.ifEmpty { listOf(firstSentence) } +
        secondSentence.sentenceParts.ifEmpty { listOf(secondSentence) }
    )
  }

  companion object {
    private const val MAX_DEPTH = 30
  }
}
