package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.CannotMergeSentencesException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Duration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Sentence

@Service
class SentenceCombinationService(val sentenceCalculationService: SentenceCalculationService) {

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
      combineTwoSentences(sentence, consecutiveSentence, workingBooking)
    }
  }

  fun combineTwoSentences(
    firstSentence: Sentence,
    secondSentence: Sentence,
    workingBooking: Booking
  ) {

    // I take 2 sentences, combine them into a single conjoined sentence
    val combinedSentence: Sentence = mergeSentences(firstSentence, secondSentence)

    sentenceCalculationService.identify(combinedSentence, workingBooking.offender)
    combinedSentence.associateSentences(mutableListOf())

    addSentenceToProfile(combinedSentence, workingBooking)

    associateExistingLinksToNewSentence(firstSentence, combinedSentence, workingBooking)
    associateExistingLinksToNewSentence(secondSentence, combinedSentence, workingBooking)

    // delete the 2 existing sentences
    deleteSentenceFromProfile(firstSentence, workingBooking)
    deleteSentenceFromProfile(secondSentence, workingBooking)

    removeSelfFromConsecutiveSentences(combinedSentence)
  }

  private fun removeSelfFromConsecutiveSentences(sentence: Sentence) {
    for (UUID in sentence.consecutiveSentenceUUIDs) {
      if (UUID == sentence.identifier) {
        sentence.consecutiveSentenceUUIDs.remove(UUID)
      }
    }
    for (sentenceObject in sentence.consecutiveSentences) {
      if (sentenceObject == sentence) {
        sentence.consecutiveSentences.remove(sentenceObject)
      }
    }
  }

  private fun associateExistingLinksToNewSentence(
    originalSentence: Sentence,
    replacementSentence: Sentence,
    booking: Booking
  ) {

    booking.sentences.forEach { sentence ->
      sentence.consecutiveSentences.remove(originalSentence)
      sentence.consecutiveSentences.add(replacementSentence)
      sentence.consecutiveSentenceUUIDs.remove(originalSentence.identifier)
      sentence.consecutiveSentenceUUIDs.add(replacementSentence.identifier)
    }
  }

  private fun addSentenceToProfile(sentence: Sentence, booking: Booking) {
    booking.sentences.add(sentence)
  }

  private fun deleteSentenceFromProfile(sentence: Sentence, booking: Booking) {
    booking.sentences.remove(sentence)
  }

  fun mergeSentences(firstSentence: Sentence, secondSentence: Sentence): Sentence {

    if (!firstSentence.canMergeWith(secondSentence)) {
      throw CannotMergeSentencesException("Incompatible sentence types")
    }

    val earliestOffence = if (firstSentence.offence.startedAt.isBefore(secondSentence.offence.startedAt)) {
      firstSentence.offence
    } else {
      secondSentence.offence
    }
    val sentencedAt = if (firstSentence.sentencedAt.isBefore(secondSentence.sentencedAt)) {
      firstSentence.sentencedAt
    } else {
      secondSentence.sentencedAt
    }
    val combinedDuration = Duration()
      .appendAll(firstSentence.duration.durationElements)
      .appendAll(secondSentence.duration.durationElements)

    return Sentence(
      earliestOffence,
      combinedDuration,
      sentencedAt
    )
  }
}
