package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import org.threeten.extra.LocalDateRange
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.math.roundToLong

data class Sentence(
  val offence: Offence,
  val duration: Duration,
  val sentencedAt: LocalDate,
  val remandInDays: Int,
  val taggedBailInDays: Int,
  var identifier: UUID = UUID.randomUUID()
) {
  fun durationIsGreaterThan(length: Long, period: ChronoUnit): Boolean {

    return (
      duration.getLengthInDays(this.sentencedAt) >
        ChronoUnit.DAYS.between(this.sentencedAt, this.sentencedAt.plus(length, period))
      )
  }

  fun getHalfSentenceDate(): LocalDate {
    val days = (duration.getLengthInDays(this.sentencedAt).toDouble() / 2).roundToLong()
    return this.sentencedAt.plusDays(days)
  }

  fun getDateRange(): LocalDateRange? {
    return LocalDateRange.of(sentencedAt, duration.getEndDate(sentencedAt))
  }

  lateinit var sentenceCalculation: SentenceCalculation
  lateinit var sentenceTypes: List<SentenceType>

  var concurrentSentences = mutableListOf<Sentence>()
}
