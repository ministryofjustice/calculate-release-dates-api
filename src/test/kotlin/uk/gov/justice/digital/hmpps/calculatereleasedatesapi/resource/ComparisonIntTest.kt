package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource

import com.fasterxml.jackson.databind.ObjectMapper
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.MediaType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.Comparison
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.ComparisonPerson
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CaseLoadType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ComparisonStatusValue
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ComparisonType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.DiscrepancyCategory
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.DiscrepancyImpact
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.DiscrepancyPriority
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.DiscrepancySubCategory
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.wiremock.MockPrisonClient
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Agency
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CaseLoad
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ComparisonDiscrepancySummary
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ComparisonOverview
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ComparisonPersonOverview
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ComparisonSummary
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CreateComparisonDiscrepancyRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.DiscrepancyCause
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.HdcFourPlusComparisonMismatch
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.MismatchType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ReleaseDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.ComparisonInput
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.ComparisonPersonRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.ComparisonRepository
import java.time.LocalDate

class ComparisonIntTest(private val mockPrisonClient: MockPrisonClient) : IntegrationTestBase() {

  @Autowired
  lateinit var comparisonPersonRepository: ComparisonPersonRepository

  @Autowired
  lateinit var comparisonRepository: ComparisonRepository

  @Autowired
  lateinit var objectMapper: ObjectMapper

  @BeforeEach
  fun clearTables() {
    comparisonPersonRepository.deleteAll()
    comparisonRepository.deleteAll()
    mockPrisonClient.withPrisonCalculableSentences("ABC", "ABC")
    mockPrisonClient.withInstAgencies(listOf(Agency("ABC", "prison ABC"), Agency("HDC4P", "prison HDC4P")))
  }

  @Test
  fun `Run comparison on a prison must compare all viable prisoners`() {
    val result = createComparison("ABC")

    assertEquals(ComparisonType.ESTABLISHMENT_FULL, result.comparisonType)
    assertEquals(0, result.numberOfPeopleCompared)
    val comparison = comparisonRepository.findByComparisonShortReference(result.comparisonShortReference)
    assertEquals(1, comparison!!.numberOfPeopleCompared)
    val personComparison = comparisonPersonRepository.findByComparisonIdIsAndIsMatchFalse(comparison.id)[0]
    assertTrue(personComparison.isValid)
    assertFalse(personComparison.isMatch)
    assertEquals("Z0020ZZ", personComparison.person)
  }

  @Test
  fun `Retrieve comparisons must return summary`() {
    val comparison = createComparison("ABC")
    val result = webTestClient.get()
      .uri("/comparison")
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATE_COMPARER")))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(object : ParameterizedTypeReference<List<ComparisonSummary>>() {})
      .returnResult().responseBody!!

    assertEquals(1, result.size)
    assertEquals(comparison.prison, result[0].prison)
    assertEquals(1, result[0].numberOfPeopleCompared)
    assertEquals(1, result[0].numberOfMismatches)
  }

  @Test
  fun `Retrieve comparison must return mismatches associated`() {
    val comparison = createComparison("ABC")
    val result = webTestClient.get()
      .uri("/comparison/{comparisonId}", comparison.comparisonShortReference)
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATE_COMPARER")))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(ComparisonOverview::class.java)
      .returnResult().responseBody!!

    assertEquals(comparison.prison, result.prison)
    assertEquals(1, result.numberOfPeopleCompared)
    assertEquals(1, result.numberOfMismatches)
    assertTrue(result.mismatches[0].isValid)
    assertFalse(result.mismatches[0].isMatch)
    assertEquals("Z0020ZZ", result.mismatches[0].personId)
    assertEquals("prison ABC", result.mismatches[0].establishment)
  }

  @ParameterizedTest
  @EnumSource(ComparisonType::class, names = ["ESTABLISHMENT_FULL", "ESTABLISHMENT_HDCED4PLUS"], mode = EnumSource.Mode.INCLUDE)
  fun `Retrieve comparison for HDC4+ must populate all HDC4+ dates on relevant comparison types`(comparisonType: ComparisonType) {
    mockPrisonClient
      .withCaseLoadsForMe(CaseLoad("HDC4P", "HDC4P", CaseLoadType.INST, null, currentlyActive = true))
      .withPrisonCalculableSentences("HDC4P", "PrisonWithSomeHDC4+CasesForBulkLoad")

    val comparison = createComparison("HDC4P", comparisonType)
    val result = webTestClient.get()
      .uri("/comparison/{comparisonId}", comparison.comparisonShortReference)
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATE_COMPARER")))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(ComparisonOverview::class.java)
      .returnResult().responseBody!!

    assertEquals(comparison.prison, result.prison)
    assertEquals(3, result.hdc4PlusCalculated.size)

    assertEquals(
      listOf(
        HdcFourPlusComparisonMismatch("G6390GH", "Darlonne", MismatchType.RELEASE_DATES_MISMATCH, LocalDate.of(2020, 5, 13), "prison HDC4P", ReleaseDate(LocalDate.of(2020, 11, 8), ReleaseDateType.CRD)),
        HdcFourPlusComparisonMismatch("G3229GA", "Britteman", MismatchType.RELEASE_DATES_MISMATCH, LocalDate.of(2016, 11, 4), "prison HDC4P", ReleaseDate(LocalDate.of(2017, 5, 2), ReleaseDateType.CRD)),
        HdcFourPlusComparisonMismatch("G5243GX", "Khalifer", MismatchType.RELEASE_DATES_MISMATCH, LocalDate.of(2017, 8, 7), "prison HDC4P", ReleaseDate(LocalDate.of(2018, 2, 2), ReleaseDateType.CRD)),
      ),
      result.hdc4PlusCalculated,
    )
  }

  @Test
  fun `Retrieve comparison person must return all dates`() {
    val comparison = createComparison("ABC")
    val storedComparison = comparisonRepository.findByComparisonShortReference(comparison.comparisonShortReference)
    val comparisonPerson = comparisonPersonRepository.findByComparisonIdIsAndIsMatchFalse(storedComparison!!.id)[0]
    val result = webTestClient.get()
      .uri("/comparison/{comparisonId}/mismatch/{mismatchId}", comparison.comparisonShortReference, comparisonPerson.shortReference)
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATE_COMPARER")))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(ComparisonPersonOverview::class.java)
      .returnResult().responseBody!!
    assertTrue(result.isValid)
    assertFalse(result.isMatch)
    assertEquals(comparisonPerson.person, result.personId)
  }

  @Test
  fun `Retrieve comparison person discrepancy should return discrepancy summary`() {
    val comparison = createComparison("ABC")
    val storedComparison = comparisonRepository.findByComparisonShortReference(comparison.comparisonShortReference)
    val comparisonPerson = comparisonPersonRepository.findByComparisonIdIsAndIsMatchFalse(storedComparison!!.id)[0]

    val createdDiscrepancy = createComparisonPersonDiscrepancy(comparison, comparisonPerson)

    val result = webTestClient.get()
      .uri("/comparison/{comparisonReference}/mismatch/{comparisonPersonReference}/discrepancy", comparison.comparisonShortReference, comparisonPerson.shortReference)
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATE_COMPARER")))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(ComparisonDiscrepancySummary::class.java)
      .returnResult().responseBody!!

    assertEquals(createdDiscrepancy, result)
  }

  private fun createComparison(prisonId: String, comparisonType: ComparisonType = ComparisonType.ESTABLISHMENT_FULL): Comparison {
    val request = ComparisonInput(objectMapper.createObjectNode(), prisonId, comparisonType)
    val result = webTestClient.post()
      .uri("/comparison")
      .accept(MediaType.APPLICATION_JSON)
      .bodyValue(request)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATE_COMPARER")))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(Comparison::class.java)
      .returnResult().responseBody!!
    await untilCallTo { comparisonRepository.findByComparisonShortReference(result.comparisonShortReference) } matches {
      it!!.comparisonStatus.name == ComparisonStatusValue.COMPLETED.name
    }
    return result
  }

  private fun createComparisonPersonDiscrepancy(comparison: Comparison, comparisonPerson: ComparisonPerson): ComparisonDiscrepancySummary {
    val discrepancyCauses = listOf(DiscrepancyCause(DiscrepancyCategory.PED, DiscrepancySubCategory.DATE_NOT_CALCULATED))
    val request = CreateComparisonDiscrepancyRequest(DiscrepancyImpact.POTENTIAL_UNLAWFUL_DETENTION, discrepancyCauses, "detail", DiscrepancyPriority.HIGH_RISK, "action")
    val result = webTestClient.post()
      .uri("/comparison/{comparisonReference}/mismatch/{mismatchReference}/discrepancy", comparison.comparisonShortReference, comparisonPerson.shortReference)
      .accept(MediaType.APPLICATION_JSON)
      .bodyValue(request)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATE_COMPARER")))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(ComparisonDiscrepancySummary::class.java)
      .returnResult().responseBody!!
    return result
  }
}
