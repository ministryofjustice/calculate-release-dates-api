package uk.gov.justice.digital.hmpps.offenderevents.integration.health

import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.offenderevents.integration.IntegrationTestBase

class InfoTest : IntegrationTestBase() {

  @Test
  fun `Info page is accessible`() {
    webTestClient.get()
        .uri("/info")
        .exchange()
        .expectStatus()
        .isOk
        .expectBody()
        .jsonPath("app.name").isEqualTo("Probation Offender Events")
  }

}