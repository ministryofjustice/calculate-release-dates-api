package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Duration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Sentence
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Service
class SentenceCombinationService(val sentenceIdentificationService: SentenceIdentificationService) {

  fun combineTwoSentences(
    firstSentence: Sentence,
    secondSentence: Sentence,
    workingBooking: Booking,
    sentenceMergeFunction: (Sentence, Sentence) -> Sentence
  ) {

    // I take 2 sentences, combine them into a single conjoined sentence
    val combinedSentence: Sentence = sentenceMergeFunction(firstSentence, secondSentence)

    sentenceIdentificationService.identify(combinedSentence, workingBooking.offender)
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

  fun earliestOffence(firstSentence: Sentence, secondSentence: Sentence): Offence {
    return if (firstSentence.offence.startedAt.isBefore(secondSentence.offence.startedAt)) {
      firstSentence.offence
    } else {
      secondSentence.offence
    }
  }

  fun combinedDuration(firstSentence: Sentence, secondSentence: Sentence): Duration {
    return Duration()
      .appendAll(firstSentence.duration.durationElements)
      .appendAll(secondSentence.duration.durationElements)
  }

  fun earliestSentencedAt(firstSentence: Sentence, secondSentence: Sentence): LocalDate {
    return if (firstSentence.sentencedAt.isBefore(secondSentence.sentencedAt)) {
      firstSentence.sentencedAt
    } else {
      secondSentence.sentencedAt
    }
  }

  fun latestExpiryDate(firstSentence: Sentence, secondSentence: Sentence): LocalDate? {
    return if (
      firstSentence.sentenceCalculation.expiryDate?.isAfter(secondSentence.sentenceCalculation.expiryDate) == true
    ) {
      firstSentence.sentenceCalculation.expiryDate
    } else {
      secondSentence.sentenceCalculation.expiryDate
    }
  }

  fun adjustedDuration(firstSentence: Sentence, secondSentence: Sentence): Duration {
    val durationElements: MutableMap<ChronoUnit, Long> = mutableMapOf()
    durationElements[ChronoUnit.DAYS] = ChronoUnit.DAYS.between(
      earliestSentencedAt(firstSentence, secondSentence),
      latestExpiryDate(firstSentence, secondSentence)?.plusDays(1L)
    )
    return Duration(durationElements)
  }
}
