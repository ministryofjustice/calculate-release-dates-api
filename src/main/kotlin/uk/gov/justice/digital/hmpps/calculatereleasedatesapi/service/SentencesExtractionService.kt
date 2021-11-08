package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.NoMatchingReleaseDateFoundException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Sentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceCalculation
import java.time.LocalDate
import java.util.Objects
import java.util.stream.Stream
import kotlin.reflect.KProperty1

@Service
class SentencesExtractionService {

  // show that every sentence in the schedule are overlapping
  // this is n^2 complexity and could be more efficient
  // optimise if performance required
  fun allOverlap(sentences: List<Sentence>): Boolean {
    for (sentence in sentences) {
      for (innerSentence in sentences) {
        if (!doSentencesOverlap(sentence, innerSentence)) {
          return false
        }
      }
    }
    return true
  }

  fun doSentencesOverlap(
    sentenceOne: Sentence,
    sentenceTwo: Sentence
  ): Boolean {
    if (sentenceOne.hashCode() != sentenceTwo.hashCode()) {
      log.info(
        "Is the date range {} from {} overlapping or abutting this date range {} from {} ? {} ",
        sentenceOne, sentenceOne.hashCode(), sentenceTwo.getDateRange(), sentenceTwo.hashCode(),
        sentenceOne.getDateRange()!!.isConnected(sentenceTwo.getDateRange())
      )
      if (!sentenceOne.getDateRange()!!.isConnected(sentenceTwo.getDateRange())) {
        return false
      }
    }
    return true
  }

  fun mostRecentOrNull(
    sentences: MutableList<Sentence>,
    property: KProperty1<SentenceCalculation, LocalDate?>
  ): LocalDate? {
    return sentences
      .map { property.get(it.sentenceCalculation) }
      .filter(Objects::nonNull)
      .maxOfOrNull { it!! }
  }

  fun mostRecent(
    sentences: MutableList<Sentence>,
    property: KProperty1<SentenceCalculation, LocalDate?>
  ): LocalDate {
    return sentences
      .map { property.get(it.sentenceCalculation) }
      .filter(Objects::nonNull)
      .maxOf { it!! }
  }

  fun mostRecentSentence(
    sentences: MutableList<Sentence>,
    property: KProperty1<SentenceCalculation, LocalDate?>
  ): Sentence {
    return sentences
      .filter { property.get(it.sentenceCalculation) != null }
      .maxByOrNull { property.get(it.sentenceCalculation)!! }!!
  }

  fun hasNoConsecutiveSentences(sentenceStream: Stream<Sentence>): Boolean {
    return sentenceStream.allMatch { sentence -> sentence.consecutiveSentences.isEmpty() }
  }

  fun allSentencesContainType(sentences: MutableList<Sentence>, sentenceType: SentenceType): Boolean {
    return sentences.all { it.sentenceTypes.contains(sentenceType) }
  }

  fun getAssociatedReleaseType(sentences: MutableList<Sentence>, latestReleaseDate: LocalDate?): Boolean {
    val matchingReleaseTypes = sentences
      .filter { it.sentenceCalculation.releaseDate?.equals(latestReleaseDate) == true }
      .map { it.sentenceCalculation.isReleaseDateConditional }
      .toMutableList()
    return if (matchingReleaseTypes.size > 0) {
      matchingReleaseTypes.all { it }
    } else {
      throw NoMatchingReleaseDateFoundException("Could not find release date in sentences")
    }
  }

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}
