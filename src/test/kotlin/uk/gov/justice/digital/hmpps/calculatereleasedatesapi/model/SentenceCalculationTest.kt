package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.temporal.ChronoUnit

internal class SentenceCalculationTest {

  @Test
  fun calculatedReleaseDate() {
    val duration = Duration(2.0, ChronoUnit.DAYS)
    val sentencedAt: LocalDate = LocalDate.of(2020, 1, 1)
    val sentence = Sentence(duration, sentencedAt, 0, 0)
    val sentenceCalculation = SentenceCalculation(sentence)
    val calculatedReleaseDate: LocalDate = LocalDate.of(2020, 1, 1)
    sentenceCalculation.calculatedReleaseDate = calculatedReleaseDate
    assertEquals(calculatedReleaseDate, sentenceCalculation.calculatedReleaseDate)
  }

  @Test
  fun calculatedExpiryDate() {
    val duration = Duration(2.0, ChronoUnit.DAYS)
    val sentencedAt: LocalDate = LocalDate.of(2020, 1, 1)
    val sentence = Sentence(duration, sentencedAt, 0, 0)
    val sentenceCalculation = SentenceCalculation(sentence)
    val calculatedExpiryDate: LocalDate = LocalDate.of(2020, 1, 1)
    sentenceCalculation.calculatedExpiryDate = calculatedExpiryDate
    assertEquals(calculatedExpiryDate, sentenceCalculation.calculatedExpiryDate)
  }

  @Test
  fun numberOfDaysToSentenceExpiryDate() {
    val duration = Duration(2.0, ChronoUnit.DAYS)
    val sentencedAt: LocalDate = LocalDate.of(2020, 1, 1)
    val sentence = Sentence(duration, sentencedAt, 0, 0)
    val sentenceCalculation = SentenceCalculation(sentence)
    val numberOfDaysToSentenceExpiryDate = 0
    sentenceCalculation.numberOfDaysToSentenceExpiryDate = numberOfDaysToSentenceExpiryDate
    assertEquals(numberOfDaysToSentenceExpiryDate, sentenceCalculation.numberOfDaysToSentenceExpiryDate)
  }

  @Test
  fun numberOfDaysToReleaseDate() {
    val duration = Duration(2.0, ChronoUnit.DAYS)
    val sentencedAt: LocalDate = LocalDate.of(2020, 1, 1)
    val sentence = Sentence(duration, sentencedAt, 0, 0)
    val sentenceCalculation = SentenceCalculation(sentence)
    val numberOfDaysToReleaseDate = 0
    sentenceCalculation.numberOfDaysToReleaseDate = numberOfDaysToReleaseDate
    assertEquals(numberOfDaysToReleaseDate, sentenceCalculation.numberOfDaysToReleaseDate)
  }

  @Test
  fun calculatedTotalRemandDays() {
    val duration = Duration(2.0, ChronoUnit.DAYS)
    val sentencedAt: LocalDate = LocalDate.of(2020, 1, 1)
    val sentence = Sentence(duration, sentencedAt, 0, 0)
    val sentenceCalculation = SentenceCalculation(sentence)
    val calculatedTotalRemandDays = 0
    sentenceCalculation.calculatedTotalRemandDays = calculatedTotalRemandDays
    assertEquals(calculatedTotalRemandDays, sentenceCalculation.calculatedTotalRemandDays)
  }

  @Test
  fun licenseExpiryDate() {
    val duration = Duration(2.0, ChronoUnit.DAYS)
    val sentencedAt: LocalDate = LocalDate.of(2020, 1, 1)
    val sentence = Sentence(duration, sentencedAt, 0, 0)
    val sentenceCalculation = SentenceCalculation(sentence)
    val licenseExpiryDate: LocalDate = LocalDate.of(2019, 1, 1)
    sentenceCalculation.licenseExpiryDate = licenseExpiryDate
    assertEquals(licenseExpiryDate, sentenceCalculation.licenseExpiryDate)
  }

  @Test
  fun sentenceExpiryDate() {
    val duration = Duration(2.0, ChronoUnit.DAYS)
    val sentencedAt: LocalDate = LocalDate.of(2020, 1, 1)
    val sentence = Sentence(duration, sentencedAt, 0, 0)
    val sentenceCalculation = SentenceCalculation(sentence)
    val sentenceExpiryDate: LocalDate = LocalDate.of(2019, 1, 1)
    sentenceCalculation.sentenceExpiryDate = sentenceExpiryDate
    assertEquals(sentenceExpiryDate, sentenceCalculation.sentenceExpiryDate)
  }

  @Test
  fun releaseDate() {
    val duration = Duration(2.0, ChronoUnit.DAYS)
    val sentencedAt: LocalDate = LocalDate.of(2020, 1, 1)
    val sentence = Sentence(duration, sentencedAt, 0, 0)
    val sentenceCalculation = SentenceCalculation(sentence)
    val releaseDate: LocalDate = LocalDate.of(2019, 1, 1)
    sentenceCalculation.releaseDate = releaseDate
    assertEquals(releaseDate, sentenceCalculation.releaseDate)
  }

  @Test
  fun topUpSupervisionDate() {
    val duration = Duration(2.0, ChronoUnit.DAYS)
    val sentencedAt: LocalDate = LocalDate.of(2020, 1, 1)
    val sentence = Sentence(duration, sentencedAt, 0, 0)
    val sentenceCalculation = SentenceCalculation(sentence)
    val topUpSupervisionDate: LocalDate = LocalDate.of(2019, 1, 1)
    sentenceCalculation.topUpSupervisionDate = topUpSupervisionDate
    assertEquals(topUpSupervisionDate, sentenceCalculation.topUpSupervisionDate)
  }

  @Test
  fun releaseDateConditional() {
    val duration = Duration(2.0, ChronoUnit.DAYS)
    val sentencedAt: LocalDate = LocalDate.of(2020, 1, 1)
    val sentence = Sentence(duration, sentencedAt, 0, 0)
    val sentenceCalculation = SentenceCalculation(sentence)
    val releaseDateConditional = true
    sentenceCalculation.isReleaseDateConditional = releaseDateConditional
    assertEquals(releaseDateConditional, sentenceCalculation.isReleaseDateConditional)
  }

  @Test
  fun copy() {
    val duration = Duration(2.0, ChronoUnit.DAYS)
    val sentencedAt: LocalDate = LocalDate.of(2020, 1, 1)
    val sentence = Sentence(duration, sentencedAt, 0, 0)
    val secondSentence = Sentence(duration, sentencedAt, 2, 2)
    val sentenceCalculation = SentenceCalculation(sentence)
    val secondSentenceCalculation = sentenceCalculation.copy(sentence)
    assertEquals(secondSentenceCalculation.toString(), sentenceCalculation.toString())
    secondSentenceCalculation.sentence = secondSentence
    assertEquals(sentenceCalculation.sentence, sentence)
    assertEquals(secondSentenceCalculation.sentence, secondSentence)
  }

  @Test
  fun testToString() {
    val duration = Duration(2.0, ChronoUnit.DAYS)
    val sentencedAt: LocalDate = LocalDate.of(2020, 1, 1)
    val sentence = Sentence(duration, sentencedAt, 0, 0)
    val sentenceCalculation = SentenceCalculation(sentence)
    val releaseDateConditional = true
    val releaseDate: LocalDate = LocalDate.of(2019, 1, 1)
    val topUpSupervisionDate: LocalDate = LocalDate.of(2019, 1, 1)
    sentenceCalculation.isReleaseDateConditional = releaseDateConditional
    sentenceCalculation.topUpSupervisionDate = topUpSupervisionDate
    sentenceCalculation.releaseDate = releaseDate
    assertEquals(
      "SentenceCalculation(sentence=Sentence(duration={Days=2.0}, " +
        "sentencedAt=2020-01-01, remandInDays=0, taggedBailInDays=0))",
      sentenceCalculation.toString()
    )
  }
}
