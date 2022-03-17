package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ExtractableSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceCalculation
import java.time.LocalDate
import java.util.Objects
import kotlin.reflect.KProperty1

@Service
class SentencesExtractionService {

  fun mostRecentOrNull(
    sentences: List<ExtractableSentence>,
    property: KProperty1<SentenceCalculation, LocalDate?>
  ): LocalDate? {
    return sentences
      .map { property.get(it.sentenceCalculation) }
      .filter(Objects::nonNull)
      .maxOfOrNull { it!! }
  }

  fun mostRecent(
    sentences: List<ExtractableSentence>,
    property: KProperty1<SentenceCalculation, LocalDate?>
  ): LocalDate {
    return sentences
      .map { property.get(it.sentenceCalculation) }
      .filter(Objects::nonNull)
      .maxOf { it!! }
  }

  fun mostRecentSentence(
    sentences: List<ExtractableSentence>,
    property: KProperty1<SentenceCalculation, LocalDate?>
  ): ExtractableSentence {
    return sentences
      .filter { property.get(it.sentenceCalculation) != null }
      .maxByOrNull { property.get(it.sentenceCalculation)!! }!!
  }

  fun mostRecentSentenceOrNull(
    sentences: List<ExtractableSentence>,
    property: KProperty1<SentenceCalculation, LocalDate?>,
    filter: (ExtractableSentence) -> Boolean = { true }
  ): ExtractableSentence? {
    return sentences
      .filter { property.get(it.sentenceCalculation) != null && filter(it) }
      .maxByOrNull { property.get(it.sentenceCalculation)!! }
  }

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}
