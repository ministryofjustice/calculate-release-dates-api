package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.context.jdbc.Sql
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.wiremock.MockPrisonService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Agency
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ManualCalculationEntryMode
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ManualCalculationInputResponse
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ManualCalculationResponse
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ManualEntryRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ManuallyEnteredDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.NomisCalculationReason
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.OffenderKeyDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SubmittedDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationRequestRepository
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.jvm.optionals.getOrElse
import kotlin.test.junit5.JUnit5Asserter.fail

@Sql(scripts = ["classpath:/test_data/reset-base-data.sql", "classpath:/test_data/load-base-data.sql"])
class ManualCalculationIntTest(private val mockPrisonService: MockPrisonService) : IntegrationTestBase() {

  @Autowired
  lateinit var calculationRequestRepository: CalculationRequestRepository

  @Test
  fun `Check if booking has indeterminate sentences`() {
    val hasIndeterminateSentences = webTestClient.get()
      .uri("/manual-calculation/$BOOKING_ID/has-indeterminate-sentences")
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(Boolean::class.java)
      .returnResult().responseBody!!

    assertThat(hasIndeterminateSentences).isFalse()
  }

  @Test
  fun `Storing a no dates manual entry is successful`() {
    val response = webTestClient.post()
      .uri("/manual-calculation/$PRISONER_ID")
      .bodyValue(ManualEntryRequest(listOf(ManuallyEnteredDate(ReleaseDateType.None, null)), 1L, ""))
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(ManualCalculationResponse::class.java)
      .returnResult().responseBody!!
    assertThat(response.calculationRequestId).isNotNull
  }

  @Test
  fun `Stores successful manual calculation triggered by DTO having SEC104 sentence terms`() {
    val response = webTestClient.post()
      .uri("/manual-calculation/CRS-2333-1")
      .bodyValue(
        ManualEntryRequest(
          listOf(
            ManuallyEnteredDate(
              ReleaseDateType.CRD,
              SubmittedDate(1, 1, LocalDate.now().year + 1),
            ),
          ),
          1L,
          "",
        ),
      )
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(ManualCalculationResponse::class.java)
      .returnResult().responseBody!!
    assertThat(response.calculationRequestId).isNotNull
    val calcRequest = calculationRequestRepository.findById(response.calculationRequestId).getOrElse { fail("couldn't find calc request") }
    assertThat(calcRequest.allocatedSDSTranche)
      .describedAs("CRS-2657 AC1.5 - Manual calculation should not store any tranche information")
      .isNull()
  }

  @Test
  fun `Stores successful manual calculation triggered by having invalid SHPO offence`() {
    val response = webTestClient.post()
      .uri("/manual-calculation/CRS-2333-2")
      .bodyValue(
        ManualEntryRequest(
          listOf(
            ManuallyEnteredDate(
              ReleaseDateType.CRD,
              SubmittedDate(1, 1, LocalDate.now().year + 1),
            ),
          ),
          1L,
          "",
        ),
      )
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(ManualCalculationResponse::class.java)
      .returnResult().responseBody!!
    assertThat(response.calculationRequestId).isNotNull
  }

  @Test
  fun `Confirm previous manual calculation does not exist for SHPO validation fail`() {
    val response = webTestClient.get()
      .uri("/manual-calculation/CRS-2437/has-existing-calculation")
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(Boolean::class.java)
      .returnResult().responseBody!!
    assertThat(response).isEqualTo(false)
  }

  @Test
  fun `Confirm previous manual calculation exists for SHPO validation fail`() {
    val newCalculation = webTestClient.post()
      .uri("/manual-calculation/CRS-2437")
      .bodyValue(
        ManualEntryRequest(
          reasonForCalculationId = 1,
          otherReasonDescription = "",
          selectedManualEntryDates = listOf(
            ManuallyEnteredDate(
              ReleaseDateType.PED,
              SubmittedDate(
                day = 1,
                month = 1,
                year = 2040,
              ),
            ),
          ),
        ),
      )
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(ManualCalculationResponse::class.java)
      .returnResult().responseBody!!
    assertThat(newCalculation.calculationRequestId).isNotNull

    val previousCalculationCheck = webTestClient.get()
      .uri("/manual-calculation/CRS-2437/has-existing-calculation")
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(Boolean::class.java)
      .returnResult().responseBody!!
    assertThat(previousCalculationCheck).isEqualTo(true)
  }

  @Test
  fun `Returns STANDARD mode with no dates when there is no existing manual calculation`() {
    mockPrisonService.withNomisCalculationReasons(listOf(NomisCalculationReason("NEW", "New Sentence")))
    mockPrisonService.stubKeyDates(
      BOOKING_ID,
      OffenderKeyDates(
        reasonCode = "NEW",
        calculatedAt = LocalDateTime.now(),
        comment = "From NOMIS",
        calculatedByUserId = "user1",
        calculatedByFirstName = "User",
        calculatedByLastName = "One",
      ),
    )

    val response = webTestClient.get()
      .uri("/manual-calculation/$PRISONER_ID/inputs")
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(ManualCalculationInputResponse::class.java)
      .returnResult().responseBody!!

    assertThat(response.mode).isEqualTo(ManualCalculationEntryMode.STANDARD)
    assertThat(response.manuallyEnteredDates).isEmpty()
  }

  @Test
  fun `Returns EXPRESS mode with the previously entered dates when the booking is unchanged since the last manual calculation`() {
    val manualEntryRequest = ManualEntryRequest(
      reasonForCalculationId = 1,
      otherReasonDescription = "",
      selectedManualEntryDates = listOf(
        ManuallyEnteredDate(
          ReleaseDateType.PED,
          SubmittedDate(
            day = 1,
            month = 1,
            year = 2040,
          ),
        ),
      ),
    )

    val storedCalculation = webTestClient.post()
      .uri("/manual-calculation/CRS-2437")
      .bodyValue(manualEntryRequest)
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(ManualCalculationResponse::class.java)
      .returnResult().responseBody!!

    val calculationReference = calculationRequestRepository.findById(storedCalculation.calculationRequestId)
      .getOrElse { fail("couldn't find calc request") }.calculationReference

    mockPrisonService.withInstAgencies(listOf(Agency("ABC", "prison ABC")))

    val crs2437BookingId = "CRS-2437".hashCode().toLong()
    mockPrisonService.stubKeyDates(
      crs2437BookingId,
      OffenderKeyDates(
        reasonCode = "NEW",
        calculatedAt = LocalDateTime.now(),
        comment = "From CRDS: $calculationReference",
        paroleEligibilityDate = LocalDate.of(2040, 1, 1),
        calculatedByUserId = "user1",
        calculatedByFirstName = "User",
        calculatedByLastName = "One",
      ),
    )

    val response = webTestClient.get()
      .uri("/manual-calculation/CRS-2437/inputs")
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(ManualCalculationInputResponse::class.java)
      .returnResult().responseBody!!

    assertThat(response.mode).isEqualTo(ManualCalculationEntryMode.EXPRESS)
    assertThat(response.manuallyEnteredDates).hasSize(1)
    assertThat(response.manuallyEnteredDates.first().type).isEqualTo(ReleaseDateType.PED)
    assertThat(response.manuallyEnteredDates.first().date).isEqualTo(LocalDate.of(2040, 1, 1))
  }

  companion object {
    private const val PRISONER_ID = "default"
    val BOOKING_ID = PRISONER_ID.hashCode().toLong()
  }
}
