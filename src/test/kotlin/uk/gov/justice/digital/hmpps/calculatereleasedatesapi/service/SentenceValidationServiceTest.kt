package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.fail
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.UnsupportedSentenceException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceAndOffences
import java.time.LocalDate

class SentenceValidationServiceTest {
  private val sentenceValidationService = SentenceValidationService()

  @Test
  fun `A list of unsupported sentences will be provided in an exception`() {
    val firstSentenceAndOffences = createMinimalSentenceAndOffence(
      sentenceCalculationType = "UNSUPPORTED TYPE 1",
      sentenceTypeDescription = "An unsupported sentence type 1",
    )
    val secondSentenceAndOffences = createMinimalSentenceAndOffence(
      sentenceCalculationType = "UNSUPPORTED TYPE 2",
      sentenceTypeDescription = "An unsupported sentence type 2",
    )

    try {
      sentenceValidationService.validateSupportedSentences(listOf(firstSentenceAndOffences, secondSentenceAndOffences))
    } catch (error: UnsupportedSentenceException) {
      assertThat(error.message).contains(firstSentenceAndOffences.sentenceCalculationType)
      assertThat(error.message).contains(secondSentenceAndOffences.sentenceCalculationType)
      assertThat(error.sentenceAndOffences).contains(firstSentenceAndOffences, secondSentenceAndOffences)
      return
    }
    fail<Any>("Expected an UnsupportedSentenceException")
  }

  @Test
  fun `A list of sentences will not cause an exception`() {
    val firstSentenceAndOffences = createMinimalSentenceAndOffence(
      sentenceCalculationType = "ADIMP",
      sentenceTypeDescription = "A sentence",
    )
    val secondSentenceAndOffences = createMinimalSentenceAndOffence(
      sentenceCalculationType = "YOI_ORA",
      sentenceTypeDescription = "A sentence",
    )

    sentenceValidationService.validateSupportedSentences(listOf(firstSentenceAndOffences, secondSentenceAndOffences))
  }

  private fun createMinimalSentenceAndOffence(sentenceCalculationType: String, sentenceTypeDescription: String): SentenceAndOffences {
    return SentenceAndOffences(
      bookingId = 1,
      sentenceSequence = 1,
      lineSequence = 1,
      caseSequence = 1,
      consecutiveToSequence = 1,
      sentenceDate = SentenceValidationServiceTest.FIRST_JAN_2015,
      years = 5,
      sentenceStatus = "IMP",
      sentenceCategory = "CAT",
      sentenceCalculationType = sentenceCalculationType,
      sentenceTypeDescription = sentenceTypeDescription,
    )
  }
  private companion object {
    val FIRST_JAN_2015: LocalDate = LocalDate.of(2015, 1, 1)
  }
}
