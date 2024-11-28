package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.OverallSentenceLength
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.OverallSentenceLengthComparison
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.OverallSentenceLengthRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.OverallSentenceLengthSentence
import java.time.LocalDate

class OverallSentenceLengthServiceTest {
  private val overallSentenceLengthService = OverallSentenceLengthService()

  @Nested
  inner class CustodialDurationOnlyTests {
    @Test
    fun `Consecutive sentences only`() {
      val request = OverallSentenceLengthRequest(
        consecutiveSentences = listOf(
          OverallSentenceLengthSentence(
            custodialDuration = OverallSentenceLength(years = 2),
          ),
          OverallSentenceLengthSentence(
            custodialDuration = OverallSentenceLength(years = 3),
          ),
        ),
        concurrentSentences = emptyList(),
        overallSentenceLength = OverallSentenceLengthSentence(
          OverallSentenceLength(years = 5),
        ),
        warrantDate = SENTENCE_DATE,
      )

      val result = overallSentenceLengthService.compare(request)

      assertThat(result).isEqualTo(
        OverallSentenceLengthComparison(
          OverallSentenceLength(years = 5),
          null,
          true,
          null,
        ),
      )
    }

    @Test
    fun `Concurrent sentences`() {
      val request = OverallSentenceLengthRequest(
        consecutiveSentences = emptyList(),
        concurrentSentences = listOf(
          OverallSentenceLengthSentence(
            custodialDuration = OverallSentenceLength(years = 2),
          ),
          OverallSentenceLengthSentence(
            custodialDuration = OverallSentenceLength(years = 3),
          ),
        ),
        overallSentenceLength = OverallSentenceLengthSentence(
          OverallSentenceLength(years = 3),
        ),
        warrantDate = SENTENCE_DATE,
      )
      val result = overallSentenceLengthService.compare(request)

      assertThat(result).isEqualTo(
        OverallSentenceLengthComparison(
          OverallSentenceLength(years = 3),
          null,
          true,
          null,
        ),
      )
    }

    @Test
    fun `Concurrent and consecutive sentences`() {
      val request = OverallSentenceLengthRequest(
        consecutiveSentences = listOf(
          OverallSentenceLengthSentence(
            custodialDuration = OverallSentenceLength(years = 2),
          ),
          OverallSentenceLengthSentence(
            custodialDuration = OverallSentenceLength(years = 3),
          ),
        ),
        concurrentSentences = listOf(
          OverallSentenceLengthSentence(
            custodialDuration = OverallSentenceLength(years = 7),
          ),
        ),
        overallSentenceLength = OverallSentenceLengthSentence(
          OverallSentenceLength(years = 3),
        ),
        warrantDate = SENTENCE_DATE,
      )

      val result = overallSentenceLengthService.compare(request)

      assertThat(result).isEqualTo(
        OverallSentenceLengthComparison(
          OverallSentenceLength(years = 7),
          null,
          false,
          null,
        ),
      )
    }
  }

  @Nested
  inner class CustodialAndLicenseDurationTests {
    @Test
    fun `Consecutive sentences only`() {
      val request = OverallSentenceLengthRequest(
        consecutiveSentences = listOf(
          OverallSentenceLengthSentence(
            custodialDuration = OverallSentenceLength(years = 2),
            extensionDuration = OverallSentenceLength(years = 1),
          ),
          OverallSentenceLengthSentence(
            custodialDuration = OverallSentenceLength(years = 3),
            extensionDuration = OverallSentenceLength(years = 1),
          ),
        ),
        concurrentSentences = emptyList(),
        overallSentenceLength = OverallSentenceLengthSentence(
          custodialDuration = OverallSentenceLength(years = 5),
          extensionDuration = OverallSentenceLength(years = 2),
        ),
        warrantDate = SENTENCE_DATE,
      )

      val result = overallSentenceLengthService.compare(request)

      assertThat(result).isEqualTo(
        OverallSentenceLengthComparison(
          OverallSentenceLength(years = 5),
          OverallSentenceLength(years = 2),
          true,
          true,
        ),
      )
    }

    @Test
    fun `Concurrent sentences`() {
      val request = OverallSentenceLengthRequest(
        consecutiveSentences = emptyList(),
        concurrentSentences = listOf(
          OverallSentenceLengthSentence(
            custodialDuration = OverallSentenceLength(years = 2),
            extensionDuration = OverallSentenceLength(years = 1),
          ),
          OverallSentenceLengthSentence(
            custodialDuration = OverallSentenceLength(years = 3),
            extensionDuration = OverallSentenceLength(years = 2),
          ),
        ),
        overallSentenceLength = OverallSentenceLengthSentence(
          custodialDuration = OverallSentenceLength(years = 3),
          extensionDuration = OverallSentenceLength(years = 2),
        ),
        warrantDate = SENTENCE_DATE,
      )
      val result = overallSentenceLengthService.compare(request)

      assertThat(result).isEqualTo(
        OverallSentenceLengthComparison(
          OverallSentenceLength(years = 3),
          OverallSentenceLength(years = 2),
          true,
          true,
        ),
      )
    }

    @Test
    fun `Concurrent sentences longest custodial is not longest license`() {
      val request = OverallSentenceLengthRequest(
        consecutiveSentences = emptyList(),
        concurrentSentences = listOf(
          OverallSentenceLengthSentence(
            custodialDuration = OverallSentenceLength(years = 8),
            extensionDuration = OverallSentenceLength(years = 2),
          ),
          OverallSentenceLengthSentence(
            custodialDuration = OverallSentenceLength(years = 7),
            extensionDuration = OverallSentenceLength(years = 4),
          ),
        ),
        overallSentenceLength = OverallSentenceLengthSentence(
          custodialDuration = OverallSentenceLength(years = 8),
          extensionDuration = OverallSentenceLength(years = 3),
        ),
        warrantDate = SENTENCE_DATE,
      )
      val result = overallSentenceLengthService.compare(request)

      assertThat(result).isEqualTo(
        OverallSentenceLengthComparison(
          OverallSentenceLength(years = 8),
          OverallSentenceLength(years = 3),
          true,
          true,
        ),
      )
    }
  }

  companion object {
    val SENTENCE_DATE: LocalDate = LocalDate.of(2023, 1, 1)
  }
}
