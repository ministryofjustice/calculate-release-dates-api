package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.context.annotation.Import
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.helpers.JwtAuthHelper
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.container.PostgresContainer
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.wiremock.AdjustmentsApiExtension
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.wiremock.BankHolidayApiExtension
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.wiremock.ManageOffencesApiExtension
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.wiremock.NomisSyncMappingApiExtension
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.wiremock.OAuthExtension
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.wiremock.PrisonApiExtension
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculatedReleaseDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationFragments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationRequestModel
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationUserInputs
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.GenuineOverrideCreatedResponse
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.GenuineOverrideRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ManualCalculationResponse
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ManualEntryRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ManuallyEnteredDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RecordARecallDecisionResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RecordARecallRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RecordARecallValidationResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SubmitCalculationRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonApiSentenceAndOffences

/*
** The abstract parent class for integration tests.
**
**  It supplies : -
**     - The SpringBootTest annotation.
**     - The active profile "test"
**     - An extension class providing a Wiremock hmpps-auth server.
**     - A JwtAuthHelper function.
**     - A WebTestClient.
**     - An ObjectMapper called mapper.
**     - A logger.
*/

@ExtendWith(
  OAuthExtension::class,
  PrisonApiExtension::class,
  BankHolidayApiExtension::class,
  ManageOffencesApiExtension::class,
  AdjustmentsApiExtension::class,
  NomisSyncMappingApiExtension::class,
)
@SpringBootTest(webEnvironment = RANDOM_PORT)
@Import(TestBuildPropertiesConfiguration::class)
@ActiveProfiles("test")
open class IntegrationTestBase internal constructor() {

  @Value("\${spring.datasource.url}")
  lateinit var dbConnectionString: String

  @Value("\${spring.datasource.username}")
  lateinit var dbUsername: String

  @Value("\${spring.datasource.password}")
  lateinit var dbPassword: String

  @Suppress("SpringJavaInjectionPointsAutowiringInspection")
  @Autowired
  lateinit var webTestClient: WebTestClient

  @Autowired
  lateinit var jwtAuthHelper: JwtAuthHelper

  @Autowired
  lateinit var objectMapper: ObjectMapper

  internal fun setAuthorisation(
    user: String = "test-client",
    roles: List<String> = listOf(),
  ): (HttpHeaders) -> Unit = jwtAuthHelper.setAuthorisation(user, roles)

  protected fun createPreliminaryCalculation(prisonerId: String, userInputs: CalculationUserInputs = CalculationUserInputs()): CalculatedReleaseDates = webTestClient.post()
    .uri("/calculation/$prisonerId")
    .accept(MediaType.APPLICATION_JSON)
    .bodyValue(CalculationRequestModel(userInputs, 1L))
    .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
    .exchange()
    .expectStatus().isOk
    .expectHeader().contentType(MediaType.APPLICATION_JSON)
    .expectBody(CalculatedReleaseDates::class.java)
    .returnResult().responseBody!!

  protected fun createCalculationForRecordARecall(prisonerId: String, recordARecallRequest: RecordARecallRequest): RecordARecallDecisionResult = webTestClient.post()
    .uri("/record-a-recall/$prisonerId/decision")
    .accept(MediaType.APPLICATION_JSON)
    .headers(setAuthorisation(roles = listOf("ROLE_CALCULATE_RELEASE_DATES__RECALL__CALCULATE__RW")))
    .bodyValue(recordARecallRequest)
    .exchange()
    .expectStatus().isOk
    .expectHeader().contentType(MediaType.APPLICATION_JSON)
    .expectBody(RecordARecallDecisionResult::class.java)
    .returnResult().responseBody!!

  protected fun validateForRecordARecall(prisonerId: String): RecordARecallValidationResult = webTestClient.get()
    .uri("/record-a-recall/$prisonerId/validate")
    .accept(MediaType.APPLICATION_JSON)
    .headers(setAuthorisation(roles = listOf("ROLE_CALCULATE_RELEASE_DATES__RECALL__CALCULATE__RW")))
    .exchange()
    .expectStatus().isOk
    .expectHeader().contentType(MediaType.APPLICATION_JSON)
    .expectBody(RecordARecallValidationResult::class.java)
    .returnResult().responseBody!!

  protected fun createConfirmCalculationForPrisoner(
    calculationRequestId: Long,
  ): CalculatedReleaseDates = createConfirmCalculationForPrisoner(calculationRequestId, emptyList())

  protected fun createConfirmCalculationForPrisoner(
    calculationRequestId: Long,
    approvedDates: List<ManuallyEnteredDate>,
  ): CalculatedReleaseDates = webTestClient.post()
    .uri("/calculation/confirm/$calculationRequestId")
    .accept(MediaType.APPLICATION_JSON)
    .contentType(MediaType.APPLICATION_JSON)
    .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
    .bodyValue(objectMapper.writeValueAsString(SubmitCalculationRequest(CalculationFragments("<p>BREAKDOWN</p>"), approvedDates)))
    .exchange()
    .expectStatus().isOk
    .expectHeader().contentType(MediaType.APPLICATION_JSON)
    .expectBody(CalculatedReleaseDates::class.java)
    .returnResult().responseBody!!

  protected fun getSentencesAndOffencesForCalculation(calculationId: Long) = webTestClient.get()
    .uri("/calculation/sentence-and-offences/$calculationId")
    .accept(MediaType.APPLICATION_JSON)
    .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
    .exchange()
    .expectStatus().isOk
    .expectHeader().contentType(MediaType.APPLICATION_JSON)
    .expectBody(object : ParameterizedTypeReference<List<PrisonApiSentenceAndOffences>>() {})
    .returnResult().responseBody!!

  protected fun createManualCalculation(prisonerId: String, request: ManualEntryRequest) = webTestClient.post()
    .uri("/manual-calculation/$prisonerId")
    .bodyValue(request)
    .accept(MediaType.APPLICATION_JSON)
    .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
    .exchange()
    .expectStatus().isOk
    .expectHeader().contentType(MediaType.APPLICATION_JSON)
    .expectBody(ManualCalculationResponse::class.java)
    .returnResult().responseBody!!

  protected fun createGenuineOverride(calculationRequestIdToOverride: Long, request: GenuineOverrideRequest) = webTestClient.post()
    .uri("/genuine-override/calculation/$calculationRequestIdToOverride")
    .accept(MediaType.APPLICATION_JSON)
    .bodyValue(request)
    .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
    .exchange()
    .expectStatus().isOk
    .expectHeader().contentType(MediaType.APPLICATION_JSON)
    .expectBody(GenuineOverrideCreatedResponse::class.java)
    .returnResult().responseBody!!

  companion object {
    private val pgContainer = PostgresContainer.instance

    @JvmStatic
    @DynamicPropertySource
    fun properties(registry: DynamicPropertyRegistry) {
      pgContainer?.run {
        registry.add("spring.datasource.url", pgContainer::getJdbcUrl)
        registry.add("spring.datasource.username", pgContainer::getUsername)
        registry.add("spring.datasource.password", pgContainer::getPassword)
        registry.add("spring.flyway.url", pgContainer::getJdbcUrl)
        registry.add("spring.flyway.user", pgContainer::getUsername)
        registry.add("spring.flyway.password", pgContainer::getPassword)
      }

      System.setProperty("aws.region", "eu-west-2")
    }
  }
}
