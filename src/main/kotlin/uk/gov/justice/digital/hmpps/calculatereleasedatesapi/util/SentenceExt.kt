package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculableSentence
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.ChronoUnit.MONTHS

fun List<CalculableSentence>.findClosest12MonthOrGreaterSentence(
  sentence: CalculableSentence,
  returnToCustodyDate: LocalDate,
): CalculableSentence? = this.filter {
  it !== sentence &&
    it.durationIsGreaterThanOrEqualTo(12, MONTHS) &&
    it.sentenceCalculation.unadjustedExpiryDate.isAfterOrEqualTo(returnToCustodyDate)
}.minByOrNull {
  kotlin.math.abs(ChronoUnit.DAYS.between(it.sentenceCalculation.unadjustedExpiryDate, returnToCustodyDate))
}

fun List<CalculableSentence>.findClosestUnder12MonthSentence(
  sentence: CalculableSentence,
  returnToCustodyDate: LocalDate,
): CalculableSentence? = this.filter {
  it !== sentence &&
    it.durationIsLessThan(12, MONTHS) &&
    it.sentenceCalculation.unadjustedExpiryDate.isAfterOrEqualTo(returnToCustodyDate)
}.minByOrNull {
  kotlin.math.abs(ChronoUnit.DAYS.between(it.sentenceCalculation.unadjustedExpiryDate, returnToCustodyDate))
}
