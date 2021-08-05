package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.CannotMergeSentencesException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Duration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.OffenderSentenceProfile
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Sentence

@Service
class SentenceCombinationService {

  fun combineConsecutiveSentences(offenderSentenceProfile: OffenderSentenceProfile): OffenderSentenceProfile {
    val workingOffenderSentenceProfile: OffenderSentenceProfile = offenderSentenceProfile.copy()
    // find a list of sentences that have concurrent sentences
    val concurrentSentences = offenderSentenceProfile.sentences.filter { it.concurrentSentences.isNotEmpty() }
    for (sentence in concurrentSentences) {
      combineSentenceWithEachOfItsConcurrentSentences(sentence, workingOffenderSentenceProfile)
    }
    return workingOffenderSentenceProfile
  }

  /*
    // loop over the aggregate sentences and to add the duration of the sentence onto one new sentence
    val durations: List<Duration> = concurrentSentences.stream().map(Sentence::getDuration).collect(Collectors.toList())
    val isReleaseDateConditional: Unit =
      concurrentSentences.get(0).getSentenceCalculation().getIsReleaseDateConditional()
    val combinedDuration = Duration()
    for (duration in durations) {
      for ((key, value): Map.Entry<ChronoUnit, Double> in duration.getDurationMap().entrySet()) {
        combinedDuration.append(value, key)
      }
    }

    // delete all the concurrentSentencesStream sentences
    for (concurrentSentence in concurrentSentences) {
      offenderSentenceProfile.getSentences().remove(concurrentSentence)
    }
    val earliestSentencedAt: Unit = concurrentSentences.stream()
      .map(Sentence::getSentencedAt)
      .max(Comparator.nullsFirst(Comparator.comparing(LocalDate::toEpochDay)))
    if (earliestSentencedAt.isPresent()) {
      val sentence = Sentence(
        concurrentSentences.get(0).getOffence(),
        earliestSentencedAt.get(),
        combinedDuration,
        offenderSentenceProfile
      )
      sentence.calculate()
      // question here about how are we supposed to deal the CRD/ARD
      sentence.getSentenceCalculation().setIsReleaseDateConditional(isReleaseDateConditional)
      // add a new sentence with the new duration
      offenderSentenceProfile.addSentence(sentence)
    }

    return workingOffenderSentenceProfile
  }

   */

  private fun combineSentenceWithEachOfItsConcurrentSentences(
    sentence: Sentence,
    workingOffenderSentenceProfile: OffenderSentenceProfile
  ) {
    val concurrentSentences = sentence.concurrentSentences.toList()
    for (concurrentSentence in concurrentSentences) {
      combineTwoSentences(sentence, concurrentSentence, workingOffenderSentenceProfile)
    }
  }

  fun combineTwoSentences(
    firstSentence: Sentence,
    secondSentence: Sentence,
    workingOffenderSentenceProfile: OffenderSentenceProfile
  ) {

    // I take 2 sentences, combine them into a single conjoined sentence
    val combinedSentence: Sentence = mergeSentences(firstSentence, secondSentence)
    combinedSentence.associateSentences(mutableListOf())

    addSentenceToProfile(combinedSentence, workingOffenderSentenceProfile)

    associateExistingLinksToNewSentence(firstSentence, combinedSentence, workingOffenderSentenceProfile)
    associateExistingLinksToNewSentence(secondSentence, combinedSentence, workingOffenderSentenceProfile)

    // delete the 2 existing sentences
    deleteSentenceFromProfile(firstSentence, workingOffenderSentenceProfile)
    deleteSentenceFromProfile(secondSentence, workingOffenderSentenceProfile)

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
    offenderSentenceProfile: OffenderSentenceProfile
  ) {

    for (sentence in offenderSentenceProfile.sentences) {
      sentence.concurrentSentences.remove(originalSentence)
      sentence.concurrentSentences.add(replacementSentence)
      sentence.concurrentSentenceUUIDs.remove(originalSentence.identifier)
      sentence.concurrentSentenceUUIDs.add(replacementSentence.identifier)
    }
  }

  private fun addSentenceToProfile(sentence: Sentence, offenderSentenceProfile: OffenderSentenceProfile) {
    offenderSentenceProfile.sentences.add(sentence)
  }

  private fun deleteSentenceFromProfile(sentence: Sentence, offenderSentenceProfile: OffenderSentenceProfile) {
    offenderSentenceProfile.sentences.remove(sentence)
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
