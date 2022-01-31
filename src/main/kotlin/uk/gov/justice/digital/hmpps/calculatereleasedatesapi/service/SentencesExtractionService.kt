package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.NoMatchingReleaseDateFoundException
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
    property: KProperty1<SentenceCalculation, LocalDate?>
  ): ExtractableSentence? {
    return sentences
      .filter { property.get(it.sentenceCalculation) != null }
      .maxByOrNull { property.get(it.sentenceCalculation)!! }
  }


  fun getAssociatedReleaseType(sentences: List<ExtractableSentence>, latestReleaseDate: LocalDate?): Boolean {
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
