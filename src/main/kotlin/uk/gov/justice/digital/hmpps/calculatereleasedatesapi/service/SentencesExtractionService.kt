package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Sentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceCalculation
import java.time.LocalDate
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

  fun mostRecent(
    sentences: MutableList<Sentence>,
    property: KProperty1<SentenceCalculation, LocalDate?>
  ): LocalDate? {

    val dates = mutableListOf<LocalDate?>()
    for (sentence in sentences) {
      val dateToInsert = property.get(sentence.sentenceCalculation)
      if (dateToInsert != null) {
        dates.add(dateToInsert)
      }
    }
    return if (dates.isEmpty()) { null } else { dates.maxOf { it!! } }
  }

  fun hasNoConcurrentSentences(sentenceStream: Stream<Sentence>): Boolean {
    return sentenceStream.allMatch { sentence -> sentence.concurrentSentences.isEmpty() }
  }

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}
