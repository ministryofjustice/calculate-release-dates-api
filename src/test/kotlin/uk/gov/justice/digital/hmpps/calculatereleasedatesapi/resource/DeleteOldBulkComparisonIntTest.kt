package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.context.jdbc.Sql
import org.springframework.test.web.reactive.server.expectBody
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.dbhelper.TestTransactionalService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.wiremock.MockManageOffencesClient
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.wiremock.MockPrisonService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.manageoffencesapi.model.PcscMarkers
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.manageoffencesapi.model.SdsOffenceDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Agency
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.DeletedBulkComparisonsResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.ComparisonPersonRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.ComparisonRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.businessDaysAgo
import java.time.LocalDate

@Sql(scripts = ["classpath:/test_data/reset-base-data.sql", "classpath:/test_data/load-base-data.sql"])
class DeleteOldBulkComparisonIntTest(private val mockManageOffencesClient: MockManageOffencesClient, private val mockPrisonService: MockPrisonService) : SqsIntegrationTestBase() {

  @Autowired
  lateinit var comparisonPersonRepository: ComparisonPersonRepository

  @Autowired
  lateinit var comparisonRepository: ComparisonRepository

  @Autowired
  lateinit var testTransactionalService: TestTransactionalService

  @BeforeEach
  fun clearTables() {
    comparisonPersonRepository.deleteAll()
    comparisonRepository.deleteAll()
    mockManageOffencesClient.withSdsOffenceDetailsResponse(
      SdsOffenceDetails(
        offenceCode = "CD79009",
        pcscMarkers = PcscMarkers(inListA = false, inListB = false, inListC = false, inListD = false),
        earlyReleaseExclusions = emptyList(),
      ),
      SdsOffenceDetails(
        offenceCode = "TR68132",
        pcscMarkers = PcscMarkers(inListA = false, inListB = false, inListC = false, inListD = false),
        earlyReleaseExclusions = emptyList(),
      ),
      offences = "CD79009,TR68132",
    )
    mockPrisonService.withInstAgencies(
      listOf(
        Agency("PRIS", "prison PRIS"),
      ),
    )
  }

  @Test
  fun `Keeps the latest 2 comparisons based on age even if older than 10 business days`() {
    val comparison1 = createComparison("PRIS")
    val comparison2 = createComparison("PRIS")
    val comparison3 = createComparison("PRIS")

    testTransactionalService.setComparisonCalculatedAt(comparison1, LocalDate.now().businessDaysAgo(20).atStartOfDay())
    testTransactionalService.setComparisonCalculatedAt(comparison2, LocalDate.now().businessDaysAgo(21).atStartOfDay())
    testTransactionalService.setComparisonCalculatedAt(comparison3, LocalDate.now().businessDaysAgo(22).atStartOfDay())

    val response = webTestClient.post()
      .uri("/scheduled-task/delete-old-bulk-comparisons")
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody<DeletedBulkComparisonsResult>()
      .returnResult().responseBody!!

    assertThat(response).isEqualTo(DeletedBulkComparisonsResult(4, 4))

    assertThat(comparisonRepository.findByComparisonShortReference(comparison1.comparisonShortReference)).describedAs("comparison 1").isNotNull()
    assertThat(comparisonRepository.findByComparisonShortReference(comparison2.comparisonShortReference)).describedAs("comparison 2").isNotNull()
    assertThat(comparisonRepository.findByComparisonShortReference(comparison3.comparisonShortReference)).describedAs("comparison 3").isNull()
  }

  @Test
  fun `Keeps the all comparisons if less than 10 business days`() {
    val comparison1 = createComparison("PRIS")
    val comparison2 = createComparison("PRIS")
    val comparison3 = createComparison("PRIS")
    val comparison4 = createComparison("PRIS")

    testTransactionalService.setComparisonCalculatedAt(comparison1, LocalDate.now().businessDaysAgo(1).atStartOfDay())
    testTransactionalService.setComparisonCalculatedAt(comparison2, LocalDate.now().businessDaysAgo(2).atStartOfDay())
    testTransactionalService.setComparisonCalculatedAt(comparison3, LocalDate.now().businessDaysAgo(3).atStartOfDay())
    testTransactionalService.setComparisonCalculatedAt(comparison4, LocalDate.now().businessDaysAgo(25).atStartOfDay())

    val response = webTestClient.post()
      .uri("/scheduled-task/delete-old-bulk-comparisons")
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody<DeletedBulkComparisonsResult>()
      .returnResult().responseBody!!

    assertThat(response).isEqualTo(DeletedBulkComparisonsResult(4, 4))

    assertThat(comparisonRepository.findByComparisonShortReference(comparison1.comparisonShortReference)).describedAs("comparison 1").isNotNull()
    assertThat(comparisonRepository.findByComparisonShortReference(comparison2.comparisonShortReference)).describedAs("comparison 2").isNotNull()
    assertThat(comparisonRepository.findByComparisonShortReference(comparison3.comparisonShortReference)).describedAs("comparison 3").isNotNull()
    assertThat(comparisonRepository.findByComparisonShortReference(comparison4.comparisonShortReference)).describedAs("comparison 4").isNull()
  }
}
