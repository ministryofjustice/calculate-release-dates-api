package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.http.MediaType
import org.springframework.test.context.jdbc.Sql
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationStatus
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.wiremock.PrisonApiExtension.Companion.prisonApi
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ApprovedDatesInputResponse
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ApprovedDatesUnavailableReason
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationRequestRepository

@SpringBootTest(webEnvironment = RANDOM_PORT, properties = ["feature-toggles.use-adjustments-api=true", "feature-toggles.historic-sled=true", "feature-toggles.apply-post-recall-repeal-rules=false"])
@Sql(scripts = ["classpath:/test_data/reset-base-data.sql"])
class ApprovedDatesGetInputsIntTest : IntegrationTestBase() {
  @Autowired
  lateinit var calculationRequestRepository: CalculationRequestRepository

  private val jsonTransformation = JsonTransformation()

  @BeforeEach
  fun setUp() {
    updatedSentencesToVersion("1")
  }

  @Test
  fun `Approved dates is only available on previous calculation if the inputs have not changed`() {
    val oldPreliminaryCalc = createPreliminaryCalculation(PRISONER_ID)
    createConfirmCalculationForPrisoner(oldPreliminaryCalc.calculationRequestId)

    val approvedDatesBeforeSentenceUpdate = getApprovedDatesInput()
    assertThat(approvedDatesBeforeSentenceUpdate.approvedDatesAvailable).isTrue
    assertThat(approvedDatesBeforeSentenceUpdate.unavailableReason).isNull()
    assertThat(approvedDatesBeforeSentenceUpdate.calculatedReleaseDates).isNotNull()
    assertThat(approvedDatesBeforeSentenceUpdate.calculatedReleaseDates!!.calculationStatus).isEqualTo(CalculationStatus.PRELIMINARY)
    assertThat(approvedDatesBeforeSentenceUpdate.calculatedReleaseDates.calculationType).isEqualTo(CalculationType.CALCULATED)
    assertThat(approvedDatesBeforeSentenceUpdate.calculatedReleaseDates.calculationReason?.displayName).isEqualTo("Recording a non-calculated date (including HDCAD, APD or ROTL)")

    updatedSentencesToVersion("2")

    val approvedDatesAfterSentenceUpdate = getApprovedDatesInput()
    assertThat(approvedDatesAfterSentenceUpdate.approvedDatesAvailable).isFalse
    assertThat(approvedDatesAfterSentenceUpdate.unavailableReason).isEqualTo(ApprovedDatesUnavailableReason.INPUTS_CHANGED_SINCE_LAST_CALCULATION)
    assertThat(approvedDatesAfterSentenceUpdate.calculatedReleaseDates).isNull()
  }

  private fun getApprovedDatesInput(): ApprovedDatesInputResponse = webTestClient.get()
    .uri("/approved-dates/$PRISONER_ID/inputs")
    .accept(MediaType.APPLICATION_JSON)
    .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
    .exchange()
    .expectStatus().isOk
    .expectHeader().contentType(MediaType.APPLICATION_JSON)
    .expectBody(ApprovedDatesInputResponse::class.java)
    .returnResult().responseBody!!

  private fun updatedSentencesToVersion(version: String) {
    val sentences = jsonTransformation.getAllSentenceAndOffencesJson(version)[PRISONER_ID]!!
    prisonApi.stubGetSentencesAndOffences(PRISONER_ID.hashCode().toLong(), sentences)
  }

  companion object {
    private const val PRISONER_ID = "Approved01"
  }
}
