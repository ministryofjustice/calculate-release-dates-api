package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Duration
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * This class will aggregate the durations of consecutive sentences. This helps to calculate the length of the aggregate sentence.
 * Same units should be aggregated (months/years vs days/weeks). If units change a new duration should be created.
 *
 * e.g. a consecutive sentence of [3months, 3weeks, 3months, 5months], should return [3months, 3weeks, 8months].
 *      a consecutive sentence of [3years, 4months, 5months], should return [3years and 9months]
 */
data class ConsecutiveSentenceAggregator(val durations: List<Duration>) {

  fun aggregate(): List<Duration> {
    val aggregated = mutableListOf<Duration>()
    var workingDuration: Duration? = null
    this.durations.forEach {
      workingDuration = handleUnits(it, workingDuration, aggregated, Duration::hasMonthsOrYears, Duration::getMonthAndYearPart)
      workingDuration = handleUnits(it, workingDuration, aggregated, Duration::hasDaysOrWeeks, Duration::getDayAndWeekPart)
    }
    aggregated.add(workingDuration!!)
    return aggregated
  }

  fun calculateDays(startDate: LocalDate): Int {
    val durations = aggregate()
    var date = startDate
    durations.forEach {
      date = date.plusDays(it.getLengthInDays(date).toLong())
    }
    return ChronoUnit.DAYS.between(startDate, date).toInt()
  }

  private fun handleUnits(it: Duration, workingDuration: Duration?, aggregated: MutableList<Duration>, checkUnits: (Duration) -> Boolean, getUnits: (Duration) -> Duration): Duration? {
    val part = getUnits(it)
    if (part.isNotEmpty()) {
      return if (workingDuration == null) {
        // This is the first time round the loop, working duration not yet set, just return the part of the duration with the given units.
        part
      } else if (checkUnits(workingDuration)) {
        // The working duration has the same units as the given units, aggregate them onto the working duration.
        workingDuration.appendAll(part.durationElements)
      } else {
        // The working duration has different units to the given units, add the working duration to the list and start a new working duration with the given units.
        aggregated.add(workingDuration)
        part
      }
    }
    // If iter duration has none of the given units, return the working duration and continue.
    return workingDuration
  }
}
