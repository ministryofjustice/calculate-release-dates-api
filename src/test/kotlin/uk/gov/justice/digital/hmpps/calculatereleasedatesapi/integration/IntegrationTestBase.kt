package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.jdbc.Sql
import org.springframework.test.context.jdbc.SqlMergeMode
import org.springframework.test.web.reactive.server.WebTestClient
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.helpers.JwtAuthHelper
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.wiremock.BankHolidayApiExtension
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.wiremock.ManageOffensesApiExtension
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.wiremock.OAuthExtension
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.wiremock.PrisonApiExtension
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculatedReleaseDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationFragments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationRequestModel
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationUserInputs
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ManualEntrySelectedDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SubmitCalculationRequest

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
**     - SQL reset and load scripts to reset reference data - tests can then load what they need.
*/

@SqlMergeMode(SqlMergeMode.MergeMode.MERGE)
@Sql(
  "classpath:test_data/reset-base-data.sql",
  "classpath:test_data/load-base-data.sql",
)
@ExtendWith(OAuthExtension::class, PrisonApiExtension::class, BankHolidayApiExtension::class, ManageOffensesApiExtension::class)
@SpringBootTest(webEnvironment = RANDOM_PORT)
@Import(TestBuildPropertiesConfiguration::class)
@ActiveProfiles("test")
open class IntegrationTestBase internal constructor() {

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

  protected fun createPreliminaryCalculation(prisonerid: String): CalculatedReleaseDates = webTestClient.post()
    .uri("/calculation/$prisonerid")
    .accept(MediaType.APPLICATION_JSON)
    .bodyValue(CalculationRequestModel(CalculationUserInputs(), 1L))
    .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
    .exchange()
    .expectStatus().isOk
    .expectHeader().contentType(MediaType.APPLICATION_JSON)
    .expectBody(CalculatedReleaseDates::class.java)
    .returnResult().responseBody!!

  protected fun createConfirmCalculationForPrisoner(
    calculationRequestId: Long,
  ): CalculatedReleaseDates {
    return createConfirmCalculationForPrisoner(calculationRequestId, emptyList())
  }

  protected fun createConfirmCalculationForPrisoner(
    calculationRequestId: Long,
    approvedDates: List<ManualEntrySelectedDate>,
  ): CalculatedReleaseDates {
    return webTestClient.post()
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
  }
}
