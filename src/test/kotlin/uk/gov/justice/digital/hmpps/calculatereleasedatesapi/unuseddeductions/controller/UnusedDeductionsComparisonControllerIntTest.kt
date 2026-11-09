package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.unuseddeductions.controller

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ComparisonStatus
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.unuseddeductions.entity.UnusedDeductionsComparison
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.unuseddeductions.entity.UnusedDeductionsComparisonOutcome
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.unuseddeductions.entity.UnusedDeductionsComparisonPerson
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.unuseddeductions.entity.UnusedDeductionsComparisonType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.unuseddeductions.model.UnusedDeductionsComparisonOverview
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.unuseddeductions.model.UnusedDeductionsComparisonPage
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.unuseddeductions.model.UnusedDeductionsComparisonPersonDetail
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.unuseddeductions.repository.UnusedDeductionsComparisonPersonRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.unuseddeductions.repository.UnusedDeductionsComparisonRepository

class UnusedDeductionsComparisonControllerIntTest : IntegrationTestBase() {

  @Autowired
  lateinit var comparisonRepository: UnusedDeductionsComparisonRepository

  @Autowired
  lateinit var comparisonPersonRepository: UnusedDeductionsComparisonPersonRepository

  @Test
  fun `stored comparison results can be read back with their adjustment inputs`() {
    val comparison = comparisonRepository.save(
      UnusedDeductionsComparison(
        criteria = objectMapper.createObjectNode(),
        comparisonType = UnusedDeductionsComparisonType.MANUAL,
        calculatedByUsername = "TEST",
        comparisonStatus = ComparisonStatus.COMPLETED,
        numberOfPeopleExpected = 1,
        numberOfPeopleCompared = 1,
      ),
    )
    val person = comparisonPersonRepository.save(
      UnusedDeductionsComparisonPerson(
        comparisonId = comparison.id(),
        person = "A1234BC",
        outcome = UnusedDeductionsComparisonOutcome.MISMATCH,
        existingUnusedDeductions = 0,
        calculatedUnusedDeductions = 61,
        custodialDays = 122,
        totalDeductions = 183,
        adjustments = objectMapper.readTree("""[{"id":null,"adjustmentType":"REMAND","source":"DPS","fromDate":null,"toDate":null,"days":183,"effectiveDays":122}]"""),
        validationMessages = objectMapper.createArrayNode(),
      ),
    )

    val overview = webTestClient.get()
      .uri("/unused-deductions/comparison/${comparison.comparisonShortReference}")
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("CALCULATE_RELEASE_DATES__ADMIN__RO")))
      .exchange()
      .expectStatus().isOk
      .expectBody(UnusedDeductionsComparisonOverview::class.java)
      .returnResult().responseBody!!
    assertThat(overview.comparison.numberOfMismatches).isEqualTo(1)
    assertThat(overview.people.single().calculatedUnusedDeductions).isEqualTo(61)
    assertThat(overview.page.totalElements).isEqualTo(1)

    val filteredOut = webTestClient.get()
      .uri("/unused-deductions/comparison/${comparison.comparisonShortReference}?outcome=MATCH&prisonerId=A1234&page=0&size=10")
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("CALCULATE_RELEASE_DATES__ADMIN__RO")))
      .exchange()
      .expectStatus().isOk
      .expectBody(UnusedDeductionsComparisonOverview::class.java)
      .returnResult().responseBody!!
    assertThat(filteredOut.people).isEmpty()
    assertThat(filteredOut.page.totalElements).isEqualTo(0)
    assertThat(filteredOut.comparison.numberOfMismatches).isEqualTo(1)

    val detail = webTestClient.get()
      .uri("/unused-deductions/comparison/${comparison.comparisonShortReference}/person/${person.shortReference}")
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("CALCULATE_RELEASE_DATES__ADMIN__RO")))
      .exchange()
      .expectStatus().isOk
      .expectBody(UnusedDeductionsComparisonPersonDetail::class.java)
      .returnResult().responseBody!!
    assertThat(detail.adjustments.single().days).isEqualTo(183)
    assertThat(detail.adjustments.single().effectiveDays).isEqualTo(122)
    assertThat(detail.person.custodialDays).isEqualTo(122)

    val list = webTestClient.get()
      .uri("/unused-deductions/comparison?page=0&size=1")
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("CALCULATE_RELEASE_DATES__ADMIN__RO")))
      .exchange()
      .expectStatus().isOk
      .expectBody(UnusedDeductionsComparisonPage::class.java)
      .returnResult().responseBody!!
    assertThat(list.content).hasSize(1)
    assertThat(list.page.size).isEqualTo(1)
    assertThat(list.page.totalElements).isGreaterThanOrEqualTo(1)
  }

  @Test
  fun `a comparison needs a prison or prisoners`() {
    webTestClient.post()
      .uri("/unused-deductions/comparison")
      .contentType(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("CALCULATE_RELEASE_DATES__ADMIN__RW")))
      .bodyValue("{}")
      .exchange()
      .expectStatus().isBadRequest
  }
}
