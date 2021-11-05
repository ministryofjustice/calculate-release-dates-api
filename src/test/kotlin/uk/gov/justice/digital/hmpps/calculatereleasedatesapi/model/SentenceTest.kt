package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

internal class SentenceTest {
  @Test
  fun testSentence() {
    val sentencedAt = LocalDate.of(2020, 1, 1)
    val uuidString = "219db65e-d7b7-4c70-9239-98babff7bcd5"
    val duration = Duration()
    duration.append(1L, ChronoUnit.DAYS)
    val offence = Offence(LocalDate.of(2020, 1, 1))
    val sentence = Sentence(
      offence, duration, sentencedAt, UUID.fromString(uuidString),
      sequence = 1, sentenceParts = listOf()
    )

    assertEquals(duration, sentence.duration)
    assertEquals(sentencedAt, sentence.sentencedAt)
    assertEquals(uuidString, sentence.identifier.toString())

    assertEquals(
      "Sentence(offence=Offence(committedAt=2020-01-01, isScheduleFifteen=false), duration=1 day," +
        " sentencedAt=2020-01-01, identifier=219db65e-d7b7-4c70-9239-98babff7bcd5," +
        " consecutiveSentenceUUIDs=[], sequence=1, sentenceParts=[])",
      sentence.toString()
    )
  }

  @Test
  fun testSentenceConsecutiveSentences() {
    val sentencedAt = LocalDate.of(2020, 1, 1)
    val duration = Duration()
    duration.append(1L, ChronoUnit.DAYS)
    val offence = Offence(LocalDate.of(2020, 1, 1))
    val sentence = Sentence(
      offence, duration, sentencedAt, UUID.randomUUID(),
      mutableListOf(UUID.fromString("219db65e-d7b7-4c70-9239-98babff7bcd5"))
    )
    val secondSentence = Sentence(
      offence, duration, sentencedAt,
      UUID.fromString("219db65e-d7b7-4c70-9239-98babff7bcd5")
    )

    sentence.associateSentences(mutableListOf(sentence, secondSentence))
    assertEquals(sentence.consecutiveSentences[0], secondSentence)
  }
}
