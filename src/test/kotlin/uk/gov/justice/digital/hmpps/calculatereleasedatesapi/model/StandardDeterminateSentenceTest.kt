package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

internal class StandardDeterminateSentenceTest {
  @Test
  fun testStandardDeterminateSentence() {
    val sentencedAt = LocalDate.of(2020, 1, 1)
    val uuidString = "219db65e-d7b7-4c70-9239-98babff7bcd5"
    val duration = Duration(
      mapOf(ChronoUnit.DAYS to 1L)
    )
    val offence = Offence(LocalDate.of(2020, 1, 1))
    val standardDeterminateSentence = StandardDeterminateSentence(
      offence, duration, sentencedAt, UUID.fromString(uuidString),
      caseSequence = 1,
      lineSequence = 2,
      caseReference = "ABC123"
    )

    assertEquals(duration, standardDeterminateSentence.duration)
    assertEquals(sentencedAt, standardDeterminateSentence.sentencedAt)
    assertEquals(uuidString, standardDeterminateSentence.identifier.toString())

    assertEquals(
      "StandardDeterminateSentence(offence=Offence(committedAt=2020-01-01, isScheduleFifteen=false," +
        " isScheduleFifteenMaximumLife=false), duration=1 day, sentencedAt=2020-01-01," +
        " identifier=219db65e-d7b7-4c70-9239-98babff7bcd5, consecutiveSentenceUUIDs=[], caseSequence=1, lineSequence=2," +
        " caseReference=ABC123, recallType=null)",
      standardDeterminateSentence.toString()
    )
  }
}
