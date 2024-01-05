package uk.gov.justice.digital.hmpps.hmppstemplatepackagename.integration

import org.junit.jupiter.api.Test

class NotFoundTest : IntegrationTestBase() {

  @Test
  fun `Resources that aren't found should return 404 - test of the exception handler`() {
    webTestClient.get().uri("/some-url-not-found")
      .exchange()
      .expectStatus().isNotFound
  }
}
