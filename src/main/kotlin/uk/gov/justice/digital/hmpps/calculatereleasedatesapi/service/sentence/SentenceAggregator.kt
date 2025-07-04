package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.sentence

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculableSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Duration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.DurationAggregator
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Service
class SentenceAggregator {

  fun getDaysInGroup(startDate: LocalDate, sentences: List<CalculableSentence>, durationSupplier: (sentence: CalculableSentence) -> Duration): Int = if (sentences.all { it.isDto() }) {
    val days = DurationAggregator(sentences.map(durationSupplier)).calculateDays(startDate)
    val between = ChronoUnit.DAYS.between(startDate, startDate.plusMonths(24)).toInt()
    if (days >= between) {
      between
    } else {
      days
    }
  } else {
    DurationAggregator(sentences.map(durationSupplier)).calculateDays(startDate)
  }
}
