package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.health

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.IntegrationTestBase
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class InfoTest : IntegrationTestBase() {

  @Test
  fun `Info page is accessible`() {
    webTestClient
      .get()
      .uri("/info")
      .exchange()
      .expectStatus()
      .isOk
      .expectBody()
      .jsonPath("build.name")
      .isEqualTo("calculate-release-dates-api")
  }

  @Test
  fun `Info page reports version`() {
    webTestClient
      .get()
      .uri("/info")
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .consumeWith(System.out::println)
      .jsonPath("build.version").value<String> {
        assertThat(it).startsWith(LocalDateTime.now().format(DateTimeFormatter.ISO_DATE))
      }
  }
}
