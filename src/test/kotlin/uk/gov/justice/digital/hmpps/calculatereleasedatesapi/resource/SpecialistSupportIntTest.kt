package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculatedReleaseDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.GenuineOverrideDateRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.GenuineOverrideDateResponse
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.GenuineOverrideRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.GenuineOverrideResponse
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ManualEntryRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ManuallyEnteredDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SubmittedDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.GenuineOverrideRepository
import java.util.UUID

class SpecialistSupportIntTest : IntegrationTestBase() {

  @Autowired
  lateinit var genuineOverrideRepository: GenuineOverrideRepository

  @Test
  fun `Store a genuine override record`() {
    val preliminaryCalculation = createPreliminaryCalculation(CalculationIntTest.PRISONER_ID)
    val responseBody = webTestClient.post()
      .uri("/specialist-support/genuine-override")
      .accept(MediaType.APPLICATION_JSON)
      .bodyValue(
        GenuineOverrideRequest(
          "a reason",
          preliminaryCalculation.calculationReference.toString(),
          null,
          false,
        ),
      )
      .headers(setAuthorisation(roles = listOf("ROLE_CRDS_SPECIALIST_SUPPORT")))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(GenuineOverrideResponse::class.java)
      .returnResult().responseBody
    assertThat(responseBody).isNotNull
    assertThat(responseBody!!.originalCalculationRequest).isEqualTo(preliminaryCalculation.calculationReference.toString())
  }

  @Test
  fun `Store an overridden calculation`() {
    val preliminaryCalculation = createPreliminaryCalculation(CalculationIntTest.PRISONER_ID)
    createGenuineOverride(preliminaryCalculation.calculationReference.toString(), true)
    val responseBody = createGenuineOverrideDates(preliminaryCalculation)
    assertThat(responseBody!!.calculationReference).isNotNull
    assertThat(responseBody.originalCalculationReference).isEqualTo(preliminaryCalculation.calculationReference.toString())
    val overrides = genuineOverrideRepository.findAllByOriginalCalculationRequestCalculationReferenceOrderBySavedAtDesc(
      preliminaryCalculation.calculationReference,
    )
    assertThat(overrides.size).isEqualTo(1)
    assertThat(overrides[0].isOverridden).isTrue
    assertThat(overrides[0].savedCalculation).isNotNull
  }

  @Test
  fun `Store an overridden calculation with the saved calculation`() {
    val preliminaryCalculation = createPreliminaryCalculation(CalculationIntTest.PRISONER_ID)
    val secondCalculation = createPreliminaryCalculation(CalculationIntTest.PRISONER_ID)
    val responseBody = createGenuineOverride(
      preliminaryCalculation.calculationReference.toString(),
      true,
      secondCalculation.calculationReference.toString(),
    )
    assertThat(responseBody!!.savedCalculation).isNotNull
    assertThat(responseBody.originalCalculationRequest).isEqualTo(preliminaryCalculation.calculationReference.toString())
    val overrides = genuineOverrideRepository.findAllByOriginalCalculationRequestCalculationReferenceOrderBySavedAtDesc(
      preliminaryCalculation.calculationReference,
    )
    assertThat(overrides.size).isEqualTo(1)
    assertThat(overrides[0].isOverridden).isTrue
    assertThat(overrides[0].savedCalculation!!.calculationReference).isEqualTo(secondCalculation.calculationReference)
    assertThat(overrides[0].originalCalculationRequest.calculationReference).isEqualTo(preliminaryCalculation.calculationReference)
  }

  @Test
  fun `Retrieve an overridden calculation`() {
    val preliminaryCalculation = createPreliminaryCalculation(CalculationIntTest.PRISONER_ID)
    createGenuineOverride(preliminaryCalculation.calculationReference.toString(), true)
    val responseBody = createGenuineOverrideDates(preliminaryCalculation)
    val storedOverride = webTestClient.get()
      .uri("/specialist-support/genuine-override/calculation/${responseBody!!.calculationReference}")
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_CRDS_SPECIALIST_SUPPORT")))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(GenuineOverrideResponse::class.java)
      .returnResult().responseBody
    assertThat(storedOverride).isNotNull
    assertThat(storedOverride!!.savedCalculation).isEqualTo(responseBody.calculationReference)
  }

  @Test
  fun `Throw 404 if an overridden calculation cannot be found`() {
    webTestClient.get()
      .uri("/specialist-support/genuine-override/calculation/${UUID.randomUUID()}")
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_CRDS_SPECIALIST_SUPPORT")))
      .exchange()
      .expectStatus().isNotFound
  }

  private fun createGenuineOverrideDates(preliminaryCalculation: CalculatedReleaseDates): GenuineOverrideDateResponse? = webTestClient.post()
    .uri("/specialist-support/genuine-override/calculation")
    .accept(MediaType.APPLICATION_JSON)
    .bodyValue(
      GenuineOverrideDateRequest(
        originalCalculationReference = preliminaryCalculation.calculationReference.toString(),
        manualEntryRequest = ManualEntryRequest(
          listOf(
            ManuallyEnteredDate(ReleaseDateType.APD, SubmittedDate(day = 1, month = 2, year = 2023)),
          ),
          1L,
          "",
        ),
      ),
    )
    .headers(setAuthorisation(roles = listOf("ROLE_CRDS_SPECIALIST_SUPPORT")))
    .exchange()
    .expectStatus().isOk
    .expectHeader().contentType(MediaType.APPLICATION_JSON)
    .expectBody(GenuineOverrideDateResponse::class.java)
    .returnResult().responseBody

  private fun createGenuineOverride(
    calculationReference: String,
    isOverridden: Boolean,
    savedCalculationReference: String? = null,
  ) = webTestClient.post()
    .uri("/specialist-support/genuine-override")
    .accept(MediaType.APPLICATION_JSON)
    .bodyValue(
      GenuineOverrideRequest(
        "a reason",
        calculationReference,
        savedCalculationReference,
        isOverridden,
      ),
    )
    .headers(setAuthorisation(roles = listOf("ROLE_CRDS_SPECIALIST_SUPPORT")))
    .exchange()
    .expectStatus().isOk
    .expectHeader().contentType(MediaType.APPLICATION_JSON)
    .expectBody(GenuineOverrideResponse::class.java)
    .returnResult().responseBody

  companion object {
    const val PRISONER_ID = "default"
    const val PRISONER_ERROR_ID = "123CBA"
    val BOOKING_ID = PRISONER_ID.hashCode().toLong()
    val BOOKING_ERROR_ID = PRISONER_ERROR_ID.hashCode().toLong()
  }
}
