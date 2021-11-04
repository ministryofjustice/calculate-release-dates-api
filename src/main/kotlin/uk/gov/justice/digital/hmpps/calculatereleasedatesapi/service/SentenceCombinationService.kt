package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Duration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Sentence
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

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

    combineConsecutiveSentences(firstSentence, secondSentence, combinedSentence)

    addSentenceToBooking(combinedSentence, workingBooking)

    // delete the 2 existing sentences
    deleteSentenceFromBooking(firstSentence, workingBooking)
    deleteSentenceFromBooking(secondSentence, workingBooking)

    associateExistingLinksToNewSentence(firstSentence, combinedSentence, workingBooking)
    associateExistingLinksToNewSentence(secondSentence, combinedSentence, workingBooking)
  }

    /*
      Adds any sentences to the combinedSentence that were consecutive to the firstSentence and secondSentence
     */
  private fun combineConsecutiveSentences(
    firstSentence: Sentence,
    secondSentence: Sentence,
    combinedSentence: Sentence
  ) {

    val consecutiveSentenceUUIDSToMergedSentence: MutableList<UUID> = mutableListOf()
    val consecutiveSentencesToMergedSentence: MutableList<Sentence> = mutableListOf()

    firstSentence.consecutiveSentenceUUIDs.forEach {
      if (secondSentence.identifier != it) {
        consecutiveSentenceUUIDSToMergedSentence.add(it)
      }
    }
    secondSentence.consecutiveSentenceUUIDs.forEach {
      if (firstSentence.identifier != it) {
        consecutiveSentenceUUIDSToMergedSentence.add(it)
      }
    }
    firstSentence.consecutiveSentences.forEach {
      if (consecutiveSentenceUUIDSToMergedSentence.contains(it.identifier)) {
        consecutiveSentencesToMergedSentence.add(it)
      }
    }
    secondSentence.consecutiveSentences.forEach {
      if (consecutiveSentenceUUIDSToMergedSentence.contains(it.identifier)) {
        consecutiveSentencesToMergedSentence.add(it)
      }
    }

    combinedSentence.consecutiveSentenceUUIDs = consecutiveSentenceUUIDSToMergedSentence
    combinedSentence.consecutiveSentences = consecutiveSentencesToMergedSentence
  }

    /*
      If our orignal sentences were consecutive to another sentence on the booking. Updated the references so that
      the combined booking is consecutive to the other sentence.
     */
  private fun associateExistingLinksToNewSentence(
    originalSentence: Sentence,
    replacementSentence: Sentence,
    booking: Booking
  ) {

    booking.sentences.forEach { sentence ->
      if (sentence.consecutiveSentences.contains(originalSentence) &&
        sentence.consecutiveSentenceUUIDs.contains(originalSentence.identifier)
      ) {
        sentence.consecutiveSentences.remove(originalSentence)
        sentence.consecutiveSentences.add(replacementSentence)
        sentence.consecutiveSentenceUUIDs.remove(originalSentence.identifier)
        sentence.consecutiveSentenceUUIDs.add(replacementSentence.identifier)
      }
    }
  }

  private fun addSentenceToBooking(sentence: Sentence, booking: Booking) {
    booking.sentences.add(sentence)
  }

  private fun deleteSentenceFromBooking(sentence: Sentence, booking: Booking) {
    booking.sentences.remove(sentence)
  }

  fun earliestOffence(firstSentence: Sentence, secondSentence: Sentence): Offence {
    val offence = if (firstSentence.offence.committedAt.isBefore(secondSentence.offence.committedAt)) {
      firstSentence.offence
    } else {
      secondSentence.offence
    }
    offence.isScheduleFifteen = firstSentence.offence.isScheduleFifteen || secondSentence.offence.isScheduleFifteen
    return offence
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
