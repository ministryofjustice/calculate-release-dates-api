package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.OverallSentenceLength
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.OverallSentenceLengthComparison
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.OverallSentenceLengthRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.OverallSentenceLengthSentence
import java.time.LocalDate

class OverallSentenceLengthControllerIntTest : IntegrationTestBase() {

  @Test
  fun `Run overall sentence length comparison`() {
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
      warrantDate = LocalDate.of(2023, 1, 1),
    )
    val comparison: OverallSentenceLengthComparison = webTestClient.post()
      .uri("/overall-sentence-length")
      .accept(MediaType.APPLICATION_JSON)
      .contentType(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_CALCULATE_RELEASE_DATES__CALCULATE_RO")))
      .bodyValue(objectMapper.writeValueAsString(request))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(OverallSentenceLengthComparison::class.java)
      .returnResult().responseBody!!

    Assertions.assertThat(comparison).isEqualTo(
      OverallSentenceLengthComparison(
        OverallSentenceLength(years = 5),
        null,
        true,
        null,
      ),
    )
  }
}
