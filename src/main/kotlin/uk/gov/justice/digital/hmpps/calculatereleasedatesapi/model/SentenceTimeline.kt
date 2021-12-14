package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import com.fasterxml.jackson.annotation.JsonIgnore
import org.threeten.extra.LocalDateRange
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToLong

interface SentenceTimeline {
  val sentencedAt: LocalDate

  fun durationIsLessThan(length: Long, period: ChronoUnit): Boolean {
    return (
      getLengthInDays() <
        ChronoUnit.DAYS.between(this.sentencedAt, this.sentencedAt.plus(length, period))
      )
  }

  fun durationIsLessThanEqualTo(length: Long, period: ChronoUnit): Boolean {
    return (
      getLengthInDays() <=
        ChronoUnit.DAYS.between(this.sentencedAt, this.sentencedAt.plus(length, period))
      )
  }

  fun durationIsGreaterThan(length: Long, period: ChronoUnit): Boolean {
    return (
      getLengthInDays() >
        ChronoUnit.DAYS.between(this.sentencedAt, this.sentencedAt.plus(length, period))
      )
  }

  fun durationIsGreaterThanOrEqualTo(length: Long, period: ChronoUnit): Boolean {
    return (
      getLengthInDays() >=
        ChronoUnit.DAYS.between(this.sentencedAt, this.sentencedAt.plus(length, period))
      )
  }

  @JsonIgnore
  fun getHalfSentenceDate(): LocalDate {
    val days = (getLengthInDays().toDouble() / 2).roundToLong()
    return this.sentencedAt.plusDays(days)
  }

  @JsonIgnore
  fun getDateRange(): LocalDateRange {
    return LocalDateRange.of(sentencedAt, sentencedAt.plusDays(getLengthInDays().toLong()))
  }

  @JsonIgnore
  fun getLengthInDays(): Int
}
