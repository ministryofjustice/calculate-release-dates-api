package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Optional
import java.util.UUID

internal class SentenceTest {
  @Test
  fun testSentence() {
    val sentencedAt = LocalDate.of(2020, 1, 1)
    val uuidString = "219db65e-d7b7-4c70-9239-98babff7bcd5"
    val duration = Duration()
    duration.append(1L, ChronoUnit.DAYS)
    val offence = Offence(LocalDate.of(2020, 1, 1), Optional.empty())
    val sentence = Sentence(offence, duration, sentencedAt, 0, 0, identifier = UUID.fromString(uuidString))

    assertEquals(duration, sentence.duration)
    assertEquals(0, sentence.remandInDays)
    assertEquals(0, sentence.taggedBailInDays)
    assertEquals(0, sentence.unlawfullyAtLargeInDays)
    assertEquals(sentencedAt, sentence.sentencedAt)
    assertEquals(uuidString, sentence.identifier.toString())

    assertEquals(
      "Sentence(offence=Offence(startedAt=2020-01-01, endedAt=Optional.empty, isScheduleFifteen=false), " +
        "duration=1 days, sentencedAt=2020-01-01, remandInDays=0, taggedBailInDays=0, unlawfullyAtLargeInDays=0, " +
        "identifier=219db65e-d7b7-4c70-9239-98babff7bcd5)",
      sentence.toString()
    )
  }

  @Test
  fun testSentenceConcurrentSentences() {
    val sentencedAt = LocalDate.of(2020, 1, 1)
    val duration = Duration()
    duration.append(1L, ChronoUnit.DAYS)
    val offence = Offence(LocalDate.of(2020, 1, 1), Optional.empty())
    val sentence = Sentence(offence, duration, sentencedAt, 0, 0)
    val secondSentence = Sentence(offence, duration, sentencedAt, 0, 0)

    sentence.concurrentSentences.add(secondSentence)
    assertEquals(sentence.concurrentSentences[0], secondSentence)
  }
}
