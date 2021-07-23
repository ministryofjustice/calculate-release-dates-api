package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.temporal.ChronoUnit

internal class SentenceTest {
  @Test
  fun testSentence() {
    val sentencedAt = LocalDate.of(2020, 1, 1)
    val duration = Duration(1.0, ChronoUnit.DAYS)
    val sentence = Sentence(duration, sentencedAt, 0, 0)
    val uuidPattern = Regex("([a-f0-9]{8}(-[a-f0-9]{4}){4}[a-f0-9]{8})")

    assertEquals(sentence.duration, duration)
    assertEquals(sentence.remandInDays, 0)
    assertEquals(sentence.taggedBailInDays, 0)
    assertEquals(sentence.sentencedAt, sentencedAt)
    assertTrue(uuidPattern.matches(sentence.identifier.toString()))

    assertEquals(
      "Sentence(duration={Days=1.0}, sentencedAt=2020-01-01, remandInDays=0, taggedBailInDays=0)",
      sentence.toString()
    )
  }

  @Test
  fun testSentenceConcurrentSentences() {
    val sentencedAt = LocalDate.of(2020, 1, 1)
    val duration = Duration(1.0, ChronoUnit.DAYS)
    val sentence = Sentence(duration, sentencedAt, 0, 0)
    val secondSentence = Sentence(duration, sentencedAt, 0, 0)

    sentence.concurrentSentences.add(secondSentence)
    assertEquals(sentence.concurrentSentences[0], secondSentence)
  }
}
