package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.CannotMergeSentencesException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Duration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Sentence

@Service
class SentenceCombinationService {

  fun combineConsecutiveSentences(booking: Booking): Booking {
    val workingBooking: Booking = booking.copy()
    // find a list of sentences that have concurrent sentences
    val concurrentSentences = booking.sentences.filter { it.concurrentSentences.isNotEmpty() }
    for (sentence in concurrentSentences) {
      combineSentenceWithEachOfItsConcurrentSentences(sentence, workingBooking)
    }
    return workingBooking
  }

  private fun combineSentenceWithEachOfItsConcurrentSentences(
    sentence: Sentence,
    workingBooking: Booking
  ) {
    val concurrentSentences = sentence.concurrentSentences.toList()
    for (concurrentSentence in concurrentSentences) {
      combineTwoSentences(sentence, concurrentSentence, workingBooking)
    }
  }

  fun combineTwoSentences(
    firstSentence: Sentence,
    secondSentence: Sentence,
    workingBooking: Booking
  ) {

    // I take 2 sentences, combine them into a single conjoined sentence
    val combinedSentence: Sentence = mergeSentences(firstSentence, secondSentence)
    combinedSentence.associateSentences(mutableListOf())

    addSentenceToProfile(combinedSentence, workingBooking)

    associateExistingLinksToNewSentence(firstSentence, combinedSentence, workingBooking)
    associateExistingLinksToNewSentence(secondSentence, combinedSentence, workingBooking)

    // delete the 2 existing sentences
    deleteSentenceFromProfile(firstSentence, workingBooking)
    deleteSentenceFromProfile(secondSentence, workingBooking)

    removeSelfFromConcurrentSentences(combinedSentence)
  }

  private fun removeSelfFromConcurrentSentences(sentence: Sentence) {
    for (UUID in sentence.concurrentSentenceUUIDs) {
      if (UUID == sentence.identifier) {
        sentence.concurrentSentenceUUIDs.remove(UUID)
      }
    }
    for (sentenceObject in sentence.concurrentSentences) {
      if (sentenceObject == sentence) {
        sentence.concurrentSentences.remove(sentenceObject)
      }
    }
  }

  private fun associateExistingLinksToNewSentence(
    originalSentence: Sentence,
    replacementSentence: Sentence,
    booking: Booking
  ) {

    booking.sentences.forEach { sentence ->
      sentence.concurrentSentences.remove(originalSentence)
      sentence.concurrentSentences.add(replacementSentence)
      sentence.concurrentSentenceUUIDs.remove(originalSentence.identifier)
      sentence.concurrentSentenceUUIDs.add(replacementSentence.identifier)
    }
  }

  private fun addSentenceToProfile(sentence: Sentence, booking: Booking) {
    booking.sentences.add(sentence)
  }

  private fun deleteSentenceFromProfile(sentence: Sentence, booking: Booking) {
    booking.sentences.remove(sentence)
  }

  fun mergeSentences(firstSentence: Sentence, secondSentence: Sentence): Sentence {

    if (!firstSentence.sentenceTypes.containsAll(secondSentence.sentenceTypes)) {
      throw CannotMergeSentencesException("Incompatible sentence types")
    }

    val sentenceTypes = firstSentence.sentenceTypes.toMutableList()

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
    val combinedRemand = firstSentence.remandInDays + secondSentence.remandInDays
    val combinedTaggedBail = firstSentence.taggedBailInDays + secondSentence.taggedBailInDays
    val combinedDuration = Duration()
      .appendAll(firstSentence.duration.durationElements)
      .appendAll(secondSentence.duration.durationElements)

    val sentence = Sentence(
      earliestOffence,
      combinedDuration,
      sentencedAt,
      combinedRemand,
      combinedTaggedBail
    )
    sentence.sentenceTypes = sentenceTypes
    return sentence
  }
}
