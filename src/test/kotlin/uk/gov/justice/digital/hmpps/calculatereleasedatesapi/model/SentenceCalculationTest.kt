package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType
import java.time.LocalDate
import java.time.temporal.ChronoUnit

internal class SentenceCalculationTest {

  @Test
  fun `Test that SentenceCalculation initialises correctly`() {
    val sentencedAt = LocalDate.of(2020, 1, 1)
    val duration = Duration(
      mapOf(ChronoUnit.DAYS to 1L),
    )
    val offence = Offence(LocalDate.of(2020, 1, 1))
    val standardSentence = StandardDeterminateSentence(offence, duration, sentencedAt, isSDSPlus = true, hasAnSDSEarlyReleaseExclusion = SDSEarlyReleaseExclusionType.NO)
    val date = LocalDate.of(2021, 1, 1)
    val sentenceCalculation = SentenceCalculation(
      standardSentence,

      3,
      4,
      4.0,
      4,
      4.0,
      date,
      date,
      date,
      1,
      date,
      false,
      Adjustments(
        mutableMapOf(AdjustmentType.REMAND to mutableListOf(Adjustment(numberOfDays = 1, appliesToSentencesFrom = date))),
      ),
      date,
      date,
    )

    assertEquals(sentenceCalculation.sentence, standardSentence)
    assertEquals(3, sentenceCalculation.numberOfDaysToSentenceExpiryDate)
    assertEquals(4, sentenceCalculation.numberOfDaysToDeterminateReleaseDate)
    assertEquals(1, sentenceCalculation.calculatedTotalDeductedDays)
    assertEquals(0, sentenceCalculation.calculatedTotalAddedDays)
    assertEquals(date, sentenceCalculation.unadjustedExpiryDate)
    assertEquals(date, sentenceCalculation.unadjustedDeterminateReleaseDate)
    assertEquals(LocalDate.of(2020, 12, 31), sentenceCalculation.adjustedExpiryDate)
    assertEquals(LocalDate.of(2020, 12, 31), sentenceCalculation.adjustedDeterminateReleaseDate)
  }
}
