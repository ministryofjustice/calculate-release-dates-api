package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.test.context.jdbc.Sql
import org.springframework.test.web.reactive.server.expectBody
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.dbhelper.TestCalculationRequestService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.wiremock.MockPrisonService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.DeletedPreviousCalculationResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationRequestRepository
import java.time.LocalDateTime

@SpringBootTest(webEnvironment = RANDOM_PORT, properties = ["feature-toggles.use-adjustments-api=true", "feature-toggles.store-sentence-level-dates=true", "feature-toggles.storeOperativeSentenceEnvelope=true"])
@Sql(scripts = ["classpath:/test_data/reset-base-data.sql"])
class DeleteOldPreliminaryCalculationsIntTest(private val mockPrisonService: MockPrisonService) : IntegrationTestBase() {
  @Autowired
  lateinit var testCalculationRequestService: TestCalculationRequestService

  @Autowired
  lateinit var calculationRequestRepository: CalculationRequestRepository

  @BeforeEach
  fun setUp() {
    mockPrisonService.stubPostOffenderDates(PRISONER_ID.hashCode().toLong())
  }

  @Test
  fun `Removes only old preliminary calculations`() {
    val prelimOld = createPreliminaryCalculation(PRISONER_ID)
    val confirmedOld = createConfirmCalculationForPrisoner(prelimOld.calculationRequestId)
    val aDateOldEnoughToBeRemoved = LocalDateTime.now().minusDays(1).minusYears(2)
    testCalculationRequestService.updateCalculatedAt(prelimOld.calculationRequestId, aDateOldEnoughToBeRemoved)
    testCalculationRequestService.updateCalculatedAt(confirmedOld.calculationRequestId, aDateOldEnoughToBeRemoved)

    val prelimNew = createPreliminaryCalculation(PRISONER_ID)
    val confirmedNew = createConfirmCalculationForPrisoner(prelimNew.calculationRequestId)

    val response = webTestClient.get()
      .uri("/scheduled-task/delete-old-preliminary-calculations")
      .accept(APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(APPLICATION_JSON)
      .expectBody<DeletedPreviousCalculationResult>()
      .returnResult().responseBody!!

    assertThat(response).isEqualTo(DeletedPreviousCalculationResult(1, 1))
    assertThat(calculationRequestRepository.findById(prelimOld.calculationRequestId)).describedAs("Old prelim").isNotPresent
    assertThat(calculationRequestRepository.findById(confirmedOld.calculationRequestId)).describedAs("Old confirmed").isPresent
    assertThat(calculationRequestRepository.findById(prelimNew.calculationRequestId)).describedAs("New prelim").isPresent
    assertThat(calculationRequestRepository.findById(confirmedNew.calculationRequestId)).describedAs("New confirmed").isPresent
  }

  companion object {
    const val PRISONER_ID = "default"
  }
}
