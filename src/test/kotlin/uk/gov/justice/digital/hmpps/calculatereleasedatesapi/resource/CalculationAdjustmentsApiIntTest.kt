package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.http.MediaType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.SLED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BookingAdjustment
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BookingAdjustmentType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAdjustment
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAdjustmentType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi.BookingAndSentenceAdjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationRequestRepository
import java.time.LocalDate

@SpringBootTest(webEnvironment = RANDOM_PORT, properties = ["feature-toggles.useAdjustmentsApi=true"])
class CalculationAdjustmentsApiIntTest : IntegrationTestBase() {
  @Autowired
  lateinit var calculationRequestRepository: CalculationRequestRepository

  @Test
  fun `Adjustments API integration`() {
    val prisoner = "ADJ-API"
    val result = createPreliminaryCalculation(prisoner)

    assertThat(result).isNotNull
    assertThat(result.dates[SLED]).isEqualTo(LocalDate.of(2024, 12, 1))

    val storedAdjustments = webTestClient.get()
      .uri("/calculation/adjustments/${result.calculationRequestId}")
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(BookingAndSentenceAdjustments::class.java)
      .returnResult().responseBody!!

    assertThat(storedAdjustments.sentenceAdjustments.size).isEqualTo(1)
    assertThat(storedAdjustments.sentenceAdjustments[0]).isEqualTo(
      SentenceAdjustment(
        type = SentenceAdjustmentType.REMAND,
        fromDate = LocalDate.of(2023, 2, 1),
        toDate = LocalDate.of(2023, 4, 1),
        active = true,
        numberOfDays = 60,
        sentenceSequence = 1,
      ),
    )
    assertThat(storedAdjustments.bookingAdjustments.size).isEqualTo(2)
    assertThat(storedAdjustments.bookingAdjustments[0]).isEqualTo(
      BookingAdjustment(
        type = BookingAdjustmentType.ADDITIONAL_DAYS_AWARDED,
        fromDate = LocalDate.of(2017, 2, 3),
        toDate = null,
        active = true,
        numberOfDays = 23,
      ),
    )
    assertThat(storedAdjustments.bookingAdjustments[1]).isEqualTo(
      BookingAdjustment(
        type = BookingAdjustmentType.UNLAWFULLY_AT_LARGE,
        fromDate = LocalDate.of(2018, 10, 28),
        toDate = LocalDate.of(2018, 11, 3),
        active = true,
        numberOfDays = 7,
      ),
    )
  }
}
