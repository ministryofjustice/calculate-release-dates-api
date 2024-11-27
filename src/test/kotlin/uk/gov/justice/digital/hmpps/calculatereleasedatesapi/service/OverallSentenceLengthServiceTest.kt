package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Duration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.OverallSentenceLengthComparison
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.OverallSentenceLengthRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.OverallSentenceLengthSentence
import java.time.LocalDate
import java.time.temporal.ChronoUnit.YEARS

class OverallSentenceLengthServiceTest {
  private val overallSentenceLengthService = OverallSentenceLengthService()

  @Nested
  inner class CustodialDurationOnlyTests {
    @Test
    fun `Consecutive sentences only`() {
      val request = OverallSentenceLengthRequest(
        consecutiveSentences = listOf(
          OverallSentenceLengthSentence(
            custodialDuration = Duration(mapOf(YEARS to 2)),
          ),
          OverallSentenceLengthSentence(
            custodialDuration = Duration(mapOf(YEARS to 3)),
          ),
        ),
        concurrentSentences = emptyList(),
        overallSentenceLength = OverallSentenceLengthSentence(
          Duration(mapOf(YEARS to 5)),
        ),
        warrantDate = SENTENCE_DATE,
      )

      val result = overallSentenceLengthService.compare(request)

      assertThat(result).isEqualTo(
        OverallSentenceLengthComparison(
          Duration(mapOf(YEARS to 5)),
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
            custodialDuration = Duration(mapOf(YEARS to 2)),
          ),
          OverallSentenceLengthSentence(
            custodialDuration = Duration(mapOf(YEARS to 3)),
          ),
        ),
        overallSentenceLength = OverallSentenceLengthSentence(
          Duration(mapOf(YEARS to 3)),
        ),
        warrantDate = SENTENCE_DATE,
      )
      val result = overallSentenceLengthService.compare(request)

      assertThat(result).isEqualTo(
        OverallSentenceLengthComparison(
          Duration(mapOf(YEARS to 3)),
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
            custodialDuration = Duration(mapOf(YEARS to 2)),
          ),
          OverallSentenceLengthSentence(
            custodialDuration = Duration(mapOf(YEARS to 3)),
          ),
        ),
        concurrentSentences = listOf(
          OverallSentenceLengthSentence(
            custodialDuration = Duration(mapOf(YEARS to 7)),
          ),
        ),
        overallSentenceLength = OverallSentenceLengthSentence(
          Duration(mapOf(YEARS to 3)),
        ),
        warrantDate = SENTENCE_DATE,
      )

      val result = overallSentenceLengthService.compare(request)

      assertThat(result).isEqualTo(
        OverallSentenceLengthComparison(
          Duration(mapOf(YEARS to 7)),
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
            custodialDuration = Duration(mapOf(YEARS to 2)),
            extensionDuration = Duration(mapOf(YEARS to 1)),
          ),
          OverallSentenceLengthSentence(
            custodialDuration = Duration(mapOf(YEARS to 3)),
            extensionDuration = Duration(mapOf(YEARS to 1)),
          ),
        ),
        concurrentSentences = emptyList(),
        overallSentenceLength = OverallSentenceLengthSentence(
          custodialDuration = Duration(mapOf(YEARS to 5)),
          extensionDuration = Duration(mapOf(YEARS to 2)),
        ),
        warrantDate = SENTENCE_DATE,
      )

      val result = overallSentenceLengthService.compare(request)

      assertThat(result).isEqualTo(
        OverallSentenceLengthComparison(
          Duration(mapOf(YEARS to 5)),
          Duration(mapOf(YEARS to 2)),
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
            custodialDuration = Duration(mapOf(YEARS to 2)),
            extensionDuration = Duration(mapOf(YEARS to 1)),
          ),
          OverallSentenceLengthSentence(
            custodialDuration = Duration(mapOf(YEARS to 3)),
            extensionDuration = Duration(mapOf(YEARS to 2)),
          ),
        ),
        overallSentenceLength = OverallSentenceLengthSentence(
          custodialDuration = Duration(mapOf(YEARS to 3)),
          extensionDuration = Duration(mapOf(YEARS to 2)),
        ),
        warrantDate = SENTENCE_DATE,
      )
      val result = overallSentenceLengthService.compare(request)

      assertThat(result).isEqualTo(
        OverallSentenceLengthComparison(
          Duration(mapOf(YEARS to 3)),
          Duration(mapOf(YEARS to 2)),
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
            custodialDuration = Duration(mapOf(YEARS to 8)),
            extensionDuration = Duration(mapOf(YEARS to 2)),
          ),
          OverallSentenceLengthSentence(
            custodialDuration = Duration(mapOf(YEARS to 7)),
            extensionDuration = Duration(mapOf(YEARS to 4)),
          ),
        ),
        overallSentenceLength = OverallSentenceLengthSentence(
          custodialDuration = Duration(mapOf(YEARS to 8)),
          extensionDuration = Duration(mapOf(YEARS to 3)),
        ),
        warrantDate = SENTENCE_DATE,
      )
      val result = overallSentenceLengthService.compare(request)

      assertThat(result).isEqualTo(
        OverallSentenceLengthComparison(
          Duration(mapOf(YEARS to 8)),
          Duration(mapOf(YEARS to 3)),
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
