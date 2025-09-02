package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.sentence

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
    property: KProperty1<SentenceCalculation, LocalDate?>,
  ): LocalDate? = sentences
    .map { property.get(it.sentenceCalculation) }
    .filter(Objects::nonNull)
    .maxOfOrNull { it!! }

  fun mostRecent(
    sentences: List<CalculableSentence>,
    property: KProperty1<SentenceCalculation, LocalDate?>,
  ): LocalDate = sentences
    .map { property.get(it.sentenceCalculation) }
    .filter(Objects::nonNull)
    .maxOf { it!! }

  fun mostRecentSentence(
    sentences: List<CalculableSentence>,
    property: KProperty1<SentenceCalculation, LocalDate?>,
  ): CalculableSentence = sentences
    .filter { property.get(it.sentenceCalculation) != null }
    .maxByOrNull { property.get(it.sentenceCalculation)!! }!!

  fun mostRecentSentences(
    sentences: List<CalculableSentence>,
    property: KProperty1<SentenceCalculation, LocalDate?>,
  ): List<CalculableSentence> = mostRecentSentences(sentences) { property.get(it) }

  fun mostRecentSentences(
    sentences: List<CalculableSentence>,
    supplier: (sentence: SentenceCalculation) -> LocalDate?,
  ): List<CalculableSentence> {
    val maxSentence = sentences
      .filter { supplier(it.sentenceCalculation) != null }
      .maxByOrNull { supplier(it.sentenceCalculation)!! }!!
    val max = supplier(maxSentence.sentenceCalculation)
    return sentences.filter { supplier(it.sentenceCalculation) == max }
  }

  fun mostRecentSentenceOrNull(
    sentences: List<CalculableSentence>,
    property: KProperty1<SentenceCalculation, LocalDate?>,
    filter: (CalculableSentence) -> Boolean = { true },
  ): CalculableSentence? = sentences
    .filter { property.get(it.sentenceCalculation) != null && filter(it) }
    .maxByOrNull { property.get(it.sentenceCalculation)!! }

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}
