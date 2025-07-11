package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource

import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.wiremock.MockManageOffencesClient
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.eligibility.ErsedEligibilityService
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EligibilityControllerTest(private val mockManageOffencesClient: MockManageOffencesClient) : IntegrationTestBase() {

  @Test
  fun `Ersed Eligibility should return true`() {
    mockManageOffencesClient.stubToreraOffenceCodes(listOf("ABC", "XYZ"))
    val bookingID = "default".hashCode().toLong()
    val eligibility = webTestClient.get()
      .uri("/eligibility/$bookingID/ersed")
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(ErsedEligibilityService.ErsedEligibility::class.java)
      .returnResult().responseBody!!
    assertTrue { eligibility.isValid }
  }

  @Test
  fun `Ersed Eligibility should return false`() {
    mockManageOffencesClient.stubToreraOffenceCodes(listOf("TH68010A", "XYZ"))
    val bookingID = "CRS-892".hashCode().toLong()
    val eligibility = webTestClient.get()
      .uri("/eligibility/$bookingID/ersed")
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(ErsedEligibilityService.ErsedEligibility::class.java)
      .returnResult().responseBody!!
    assertFalse { eligibility.isValid }
    assert(eligibility.reason == "EDS sentence with 19ZA offence")
  }
}
