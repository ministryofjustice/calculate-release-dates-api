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
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.dbhelper.TestTransactionalService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.wiremock.MockPrisonService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationRequestModel
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationSentenceUserInput
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationUserInputs
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.DeletedPreviousCalculationResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.UserInputType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationRequestRepository
import java.time.LocalDateTime

@SpringBootTest(webEnvironment = RANDOM_PORT)
@Sql(scripts = ["classpath:/test_data/reset-base-data.sql"])
class DeleteOldPreliminaryCalculationsIntTest(private val mockPrisonService: MockPrisonService) : IntegrationTestBase() {
  @Autowired
  lateinit var testTransactionalService: TestTransactionalService

  @Autowired
  lateinit var calculationRequestRepository: CalculationRequestRepository

  @BeforeEach
  fun setUp() {
    mockPrisonService.stubPostOffenderDates(PRISONER_ID.hashCode().toLong())
  }

  @Test
  fun `Removes only old preliminary calculations`() {
    val requestWithUserInputs = CalculationRequestModel(
      calculationReasonId = 1L,
      calculationUserInputs = CalculationUserInputs(
        calculateErsed = true,
        sentenceCalculationUserInputs = listOf(
          CalculationSentenceUserInput(1, "SX0001", UserInputType.UPDATED, true),
        ),
      ),
    )

    val prelimOld = createPreliminaryCalculation(PRISONER_ID, requestWithUserInputs)
    val confirmedOld = createConfirmCalculationForPrisoner(prelimOld.calculationRequestId)
    val aDateOldEnoughToBeRemoved = LocalDateTime.now().minusDays(1).minusYears(2)
    testTransactionalService.updateCalculatedAt(prelimOld.calculationRequestId, aDateOldEnoughToBeRemoved)
    testTransactionalService.updateCalculatedAt(confirmedOld.calculationRequestId, aDateOldEnoughToBeRemoved)

    val prelimNew = createPreliminaryCalculation(PRISONER_ID, requestWithUserInputs)
    val confirmedNew = createConfirmCalculationForPrisoner(prelimNew.calculationRequestId)

    val response = webTestClient.post()
      .uri("/scheduled-task/delete-old-preliminary-calculations")
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
