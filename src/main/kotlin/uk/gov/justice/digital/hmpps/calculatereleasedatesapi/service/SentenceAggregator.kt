package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculableSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Duration
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Service
class SentenceAggregator {

  fun getDaysInGroup(startDate: LocalDate, sentences: List<CalculableSentence>, durationSupplier: (sentence: CalculableSentence) -> Duration): Int {
    return if (sentences.all { it.isDto() }) {
      val days = DurationAggregator(sentences.map(durationSupplier)).calculateDays(startDate)
      val between = ChronoUnit.DAYS.between(startDate, startDate.plusMonths(24))
      if (days >= between) {
        between.toInt()
      } else {
        days
      }
    } else {
      DurationAggregator(sentences.map(durationSupplier)).calculateDays(startDate)
    }
  }
}
