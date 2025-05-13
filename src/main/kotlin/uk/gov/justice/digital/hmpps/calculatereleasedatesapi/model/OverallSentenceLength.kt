package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import java.time.temporal.ChronoUnit

data class OverallSentenceLength(
  val years: Long = 0,
  val months: Long = 0,
  val weeks: Long = 0,
  val days: Long = 0,
) {

  fun toDuration(): Duration = Duration(
    mapOf(
      ChronoUnit.YEARS to years,
      ChronoUnit.MONTHS to months,
      ChronoUnit.WEEKS to weeks,
      ChronoUnit.DAYS to days,
    ),
  )

  companion object {
    fun fromDuration(duration: Duration): OverallSentenceLength = OverallSentenceLength(
      duration.durationElements[ChronoUnit.YEARS] ?: 0,
      duration.durationElements[ChronoUnit.MONTHS] ?: 0,
      duration.durationElements[ChronoUnit.WEEKS] ?: 0,
      duration.durationElements[ChronoUnit.DAYS] ?: 0,
    )
  }
}
