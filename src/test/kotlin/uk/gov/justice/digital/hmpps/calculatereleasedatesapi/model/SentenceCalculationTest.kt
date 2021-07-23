package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.temporal.ChronoUnit

internal class SentenceCalculationTest {

  @Test
  fun `Test Calculated Release Date`() {
    val duration = Duration(2.0, ChronoUnit.DAYS)
    val sentencedAt: LocalDate = LocalDate.of(2020, 1, 1)
    val sentence = Sentence(duration, sentencedAt, 0, 0)
    val sentenceCalculation = SentenceCalculation(sentence)
    val calculatedReleaseDate: LocalDate = LocalDate.of(2020, 1, 1)
    sentenceCalculation.calculatedReleaseDate = calculatedReleaseDate
    assertEquals(calculatedReleaseDate, sentenceCalculation.calculatedReleaseDate)
  }

  @Test
  fun `Test setting and retrieval Calculated Expiry Date`() {
    val duration = Duration(2.0, ChronoUnit.DAYS)
    val sentencedAt: LocalDate = LocalDate.of(2020, 1, 1)
    val sentence = Sentence(duration, sentencedAt, 0, 0)
    val sentenceCalculation = SentenceCalculation(sentence)
    val calculatedExpiryDate: LocalDate = LocalDate.of(2020, 1, 1)
    sentenceCalculation.calculatedExpiryDate = calculatedExpiryDate
    assertEquals(calculatedExpiryDate, sentenceCalculation.calculatedExpiryDate)
  }

  @Test
  fun `Test setting and retrieval of Number of Days To Sentence Expiry Date`() {
    val duration = Duration(2.0, ChronoUnit.DAYS)
    val sentencedAt: LocalDate = LocalDate.of(2020, 1, 1)
    val sentence = Sentence(duration, sentencedAt, 0, 0)
    val sentenceCalculation = SentenceCalculation(sentence)
    val numberOfDaysToSentenceExpiryDate = 0
    sentenceCalculation.numberOfDaysToSentenceExpiryDate = numberOfDaysToSentenceExpiryDate
    assertEquals(numberOfDaysToSentenceExpiryDate, sentenceCalculation.numberOfDaysToSentenceExpiryDate)
  }

  @Test
  fun `Test setting and retrieval Number of Days To Release Date`() {
    val duration = Duration(2.0, ChronoUnit.DAYS)
    val sentencedAt: LocalDate = LocalDate.of(2020, 1, 1)
    val sentence = Sentence(duration, sentencedAt, 0, 0)
    val sentenceCalculation = SentenceCalculation(sentence)
    val numberOfDaysToReleaseDate = 0
    sentenceCalculation.numberOfDaysToReleaseDate = numberOfDaysToReleaseDate
    assertEquals(numberOfDaysToReleaseDate, sentenceCalculation.numberOfDaysToReleaseDate)
  }

  @Test
  fun `Test setting and retrieval of Calculated Total RemandDays`() {
    val duration = Duration(2.0, ChronoUnit.DAYS)
    val sentencedAt: LocalDate = LocalDate.of(2020, 1, 1)
    val sentence = Sentence(duration, sentencedAt, 0, 0)
    val sentenceCalculation = SentenceCalculation(sentence)
    val calculatedTotalRemandDays = 0
    sentenceCalculation.calculatedTotalRemandDays = calculatedTotalRemandDays
    assertEquals(calculatedTotalRemandDays, sentenceCalculation.calculatedTotalRemandDays)
  }

  @Test
  fun `Test setting and retrieval of Licence Expiry Date`() {
    val duration = Duration(2.0, ChronoUnit.DAYS)
    val sentencedAt: LocalDate = LocalDate.of(2020, 1, 1)
    val sentence = Sentence(duration, sentencedAt, 0, 0)
    val sentenceCalculation = SentenceCalculation(sentence)
    val licenseExpiryDate: LocalDate = LocalDate.of(2019, 1, 1)
    sentenceCalculation.licenceExpiryDate = licenseExpiryDate
    assertEquals(licenseExpiryDate, sentenceCalculation.licenceExpiryDate)
  }

  @Test
  fun `Test setting and retrieval of Sentence Expiry Date`() {
    val duration = Duration(2.0, ChronoUnit.DAYS)
    val sentencedAt: LocalDate = LocalDate.of(2020, 1, 1)
    val sentence = Sentence(duration, sentencedAt, 0, 0)
    val sentenceCalculation = SentenceCalculation(sentence)
    val sentenceExpiryDate: LocalDate = LocalDate.of(2019, 1, 1)
    sentenceCalculation.sentenceExpiryDate = sentenceExpiryDate
    assertEquals(sentenceExpiryDate, sentenceCalculation.sentenceExpiryDate)
  }

  @Test
  fun `Test setting and retrieval of Release Date`() {
    val duration = Duration(2.0, ChronoUnit.DAYS)
    val sentencedAt: LocalDate = LocalDate.of(2020, 1, 1)
    val sentence = Sentence(duration, sentencedAt, 0, 0)
    val sentenceCalculation = SentenceCalculation(sentence)
    val releaseDate: LocalDate = LocalDate.of(2019, 1, 1)
    sentenceCalculation.releaseDate = releaseDate
    assertEquals(releaseDate, sentenceCalculation.releaseDate)
  }

  @Test
  fun `Test setting and retrieval of Top Up Supervision Date`() {
    val duration = Duration(2.0, ChronoUnit.DAYS)
    val sentencedAt: LocalDate = LocalDate.of(2020, 1, 1)
    val sentence = Sentence(duration, sentencedAt, 0, 0)
    val sentenceCalculation = SentenceCalculation(sentence)
    val topUpSupervisionDate: LocalDate = LocalDate.of(2019, 1, 1)
    sentenceCalculation.topUpSupervisionDate = topUpSupervisionDate
    assertEquals(topUpSupervisionDate, sentenceCalculation.topUpSupervisionDate)
  }

  @Test
  fun `Test setting and retrieval of whether the Release Date is conditional`() {
    val duration = Duration(2.0, ChronoUnit.DAYS)
    val sentencedAt: LocalDate = LocalDate.of(2020, 1, 1)
    val sentence = Sentence(duration, sentencedAt, 0, 0)
    val sentenceCalculation = SentenceCalculation(sentence)
    val releaseDateConditional = true
    sentenceCalculation.isReleaseDateConditional = releaseDateConditional
    assertEquals(releaseDateConditional, sentenceCalculation.isReleaseDateConditional)
  }

  @Test
  fun `Test the copy function`() {
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
  fun `Test the toString function behaves as expected`() {
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
