package uk.gov.justice.digital.hmpps.offenderevents.integration.health

import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.offenderevents.integration.IntegrationTestBase

class HealthCheckTest : IntegrationTestBase() {

  @Test
  fun `Health page reports ok`() {
    webTestClient.get()
        .uri("/health")
        .exchange()
        .expectStatus()
        .isOk
        .expectBody()
        .jsonPath("status").isEqualTo("UP")
  }

  @Test
  fun `Health ping page is accessible`() {
    webTestClient.get()
        .uri("/health/ping")
        .exchange()
        .expectStatus()
        .isOk
        .expectBody()
        .jsonPath("status").isEqualTo("UP")
  }

  @Test
  fun `readiness reports ok`() {
    webTestClient.get()
        .uri("/health/readiness")
        .exchange()
        .expectStatus()
        .isOk
        .expectBody()
        .jsonPath("status").isEqualTo("UP")
  }

  @Test
  fun `liveness reports ok`() {
    webTestClient.get()
        .uri("/health/liveness")
        .exchange()
        .expectStatus()
        .isOk
        .expectBody()
        .jsonPath("status").isEqualTo("UP")
  }

}