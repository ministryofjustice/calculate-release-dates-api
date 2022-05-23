package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculableSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceCalculation
import java.time.LocalDate
import java.util.Objects
import kotlin.reflect.KProperty1

@Service
class SentencesExtractionService {

  fun mostRecentOrNull(
    sentences: List<CalculableSentence>,
    property: KProperty1<SentenceCalculation, LocalDate?>
  ): LocalDate? {
    return sentences
      .map { property.get(it.sentenceCalculation) }
      .filter(Objects::nonNull)
      .maxOfOrNull { it!! }
  }

  fun mostRecent(
    sentences: List<CalculableSentence>,
    property: KProperty1<SentenceCalculation, LocalDate?>
  ): LocalDate {
    return sentences
      .map { property.get(it.sentenceCalculation) }
      .filter(Objects::nonNull)
      .maxOf { it!! }
  }

  fun mostRecentSentence(
    sentences: List<CalculableSentence>,
    property: KProperty1<SentenceCalculation, LocalDate?>
  ): CalculableSentence {
    return sentences
      .filter { property.get(it.sentenceCalculation) != null }
      .maxByOrNull { property.get(it.sentenceCalculation)!! }!!
  }

  fun mostRecentSentences(
    sentences: List<CalculableSentence>,
    property: KProperty1<SentenceCalculation, LocalDate?>
  ): List<CalculableSentence> {
    val maxSentence = sentences
      .filter { property.get(it.sentenceCalculation) != null }
      .maxByOrNull { property.get(it.sentenceCalculation)!! }!!
    val max = property.get(maxSentence.sentenceCalculation)
    return sentences.filter { property.get(it.sentenceCalculation) == max }
  }

  fun mostRecentSentenceOrNull(
    sentences: List<CalculableSentence>,
    property: KProperty1<SentenceCalculation, LocalDate?>,
    filter: (CalculableSentence) -> Boolean = { true }
  ): CalculableSentence? {
    return sentences
      .filter { property.get(it.sentenceCalculation) != null && filter(it) }
      .maxByOrNull { property.get(it.sentenceCalculation)!! }
  }

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}
