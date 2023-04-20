package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration

import org.junit.jupiter.api.extension.ExtendWith
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.http.HttpHeaders
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.jdbc.Sql
import org.springframework.test.context.jdbc.SqlMergeMode
import org.springframework.test.web.reactive.server.WebTestClient
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.helpers.JwtAuthHelper
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.wiremock.BankHolidayApiExtension
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.wiremock.OAuthExtension
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.wiremock.PrisonApiExtension
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
@ExtendWith(OAuthExtension::class, PrisonApiExtension::class, BankHolidayApiExtension::class)
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("test")
class IntegrationTestBase internal constructor() {

  @Suppress("SpringJavaInjectionPointsAutowiringInspection")
  @Autowired
  lateinit var webTestClient: WebTestClient

  @Autowired
  lateinit var jwtAuthHelper: JwtAuthHelper

  internal fun setAuthorisation(
    user: String = "test-client",
    roles: List<String> = listOf(),
  ): (HttpHeaders) -> Unit = jwtAuthHelper.setAuthorisation(user, roles)

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}
