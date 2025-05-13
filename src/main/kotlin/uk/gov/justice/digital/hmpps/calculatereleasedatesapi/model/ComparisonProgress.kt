package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ComparisonStatusValue
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

data class ComparisonProgress(
  val percentageComplete: Double,
  val expectedCompletionTime: LocalDateTime?,
) {
  companion object {
    fun from(comparisonStatus: ComparisonStatusValue, numberOfPeopleCompared: Long, numberOfPeopleExpected: Long, calculatedAt: LocalDateTime): ComparisonProgress = if (comparisonStatus == ComparisonStatusValue.PROCESSING) {
      val completionRatio = (numberOfPeopleCompared.toDouble() / numberOfPeopleExpected.toDouble())
      val secondsSoFar = ChronoUnit.SECONDS.between(calculatedAt, LocalDateTime.now())

      val percentageComplete = completionRatio * 100
      val expectedCompletionTime = if (percentageComplete == 0.toDouble()) {
        null
      } else {
        calculatedAt.plusSeconds((secondsSoFar / completionRatio).toLong())
      }
      ComparisonProgress(percentageComplete, expectedCompletionTime)
    } else {
      ComparisonProgress(
        percentageComplete = 100.toDouble(),
        expectedCompletionTime = null,
      )
    }
  }
}
