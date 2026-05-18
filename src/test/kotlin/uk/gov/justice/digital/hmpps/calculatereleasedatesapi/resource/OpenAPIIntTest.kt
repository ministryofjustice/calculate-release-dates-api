package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource

import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.IntegrationTestBase

class OpenAPIIntTest : IntegrationTestBase() {

  @Test
  fun `can load open API spec`() {
    webTestClient.get()
      .uri("/v3/api-docs")
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation())
      .exchange()
      .expectStatus().isOk
  }

  @Test
  fun `can load swagger home page`() {
    webTestClient.get()
      .uri("/swagger-ui/index.html")
      .accept(MediaType.TEXT_HTML)
      .headers(setAuthorisation())
      .exchange()
      .expectStatus().isOk
  }
}
