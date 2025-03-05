package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.TestUtil
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.Comparison
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.ComparisonPerson
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.ComparisonPersonDiscrepancy
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.ComparisonPersonDiscrepancyImpact
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.ComparisonPersonDiscrepancyPriority
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.ComparisonStatus
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ComparisonStatusValue
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ComparisonType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.DiscrepancyCategory
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.DiscrepancyImpact
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.DiscrepancyPriority
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.DiscrepancySubCategory
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CreateComparisonDiscrepancyRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.DiscrepancyCause
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.MismatchType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.ComparisonPersonDiscrepancyCategoryRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.ComparisonPersonDiscrepancyRepository
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class ComparisonDiscrepancyServiceTest {
  private val comparisonPersonDiscrepancyRepository = mock<ComparisonPersonDiscrepancyRepository>()
  private val comparisonPersonDiscrepancyCategoryRepository = mock<ComparisonPersonDiscrepancyCategoryRepository>()
  private var serviceUserService = mock<ServiceUserService>()
  private val comparisonDiscrepancyService = ComparisonDiscrepancyService(
    comparisonPersonDiscrepancyRepository,
    comparisonPersonDiscrepancyCategoryRepository,
    serviceUserService,
  )
  private val objectMapper: ObjectMapper = TestUtil.objectMapper()

  @Test
  fun `Creates a comparison person discrepancy`() {
    val comparison = aComparison()
    val comparisonPerson = aComparisonPerson(
      comparison.id,
      USERNAME,
    )

    val discrepancyImpact = ComparisonPersonDiscrepancyImpact(DiscrepancyImpact.POTENTIAL_UNLAWFUL_DETENTION)
    val discrepancyPriority = ComparisonPersonDiscrepancyPriority(DiscrepancyPriority.MEDIUM_RISK)
    val discrepancy = ComparisonPersonDiscrepancy(
      1,
      comparisonPerson,
      discrepancyImpact,
      emptyList(),
      discrepancyPriority = discrepancyPriority,
      detail = "detail",
      action = "action",
      createdBy = USERNAME,
    )
    whenever(serviceUserService.getUsername()).thenReturn(USERNAME)
    whenever(comparisonPersonDiscrepancyRepository.save(any())).thenReturn(discrepancy)
    val discrepancyCause = DiscrepancyCause(DiscrepancyCategory.TUSED, DiscrepancySubCategory.REMAND_OR_UAL_RELATED)
    val discrepancyRequest = CreateComparisonDiscrepancyRequest(
      impact = discrepancyImpact.impact,
      listOf(discrepancyCause),
      detail = discrepancy.detail,
      priority = discrepancyPriority.priority,
      action = discrepancy.action,
    )
    val discrepancySummary = comparisonDiscrepancyService.createDiscrepancy(
      comparison,
      comparisonPerson,
      discrepancyRequest,
    )

    verify(comparisonPersonDiscrepancyRepository).save(any())
    verify(comparisonPersonDiscrepancyCategoryRepository).saveAll(ArgumentMatchers.anyList())
    assertEquals(discrepancyRequest.impact, discrepancySummary.impact)
    assertEquals(discrepancyRequest.priority, discrepancySummary.priority)
    assertEquals(discrepancyRequest.action, discrepancySummary.action)
    assertEquals(discrepancyRequest.detail, discrepancySummary.detail)
    val causes = discrepancySummary.causes
    assertEquals(1, causes.size)
    assertEquals(discrepancyCause.category, causes[0].category)
    assertEquals(discrepancyCause.subCategory, causes[0].subCategory)
  }

  @Test
  fun `Sets superseded id on an existing discrepancy when creating a new discrepancy`() {
    val comparison = aComparison()
    val comparisonPerson = aComparisonPerson(
      comparison.id,
      ComparisonServiceTest.USERNAME,
    )

    val discrepancyImpact = ComparisonPersonDiscrepancyImpact(DiscrepancyImpact.POTENTIAL_UNLAWFUL_DETENTION)
    val discrepancyPriority = ComparisonPersonDiscrepancyPriority(DiscrepancyPriority.MEDIUM_RISK)
    val discrepancy = ComparisonPersonDiscrepancy(
      2,
      comparisonPerson,
      discrepancyImpact,
      emptyList(),
      discrepancyPriority = discrepancyPriority,
      detail = "detail",
      action = "new action",
      createdBy = USERNAME,
    )
    val existingDiscrepancy = ComparisonPersonDiscrepancy(
      1,
      comparisonPerson,
      discrepancyImpact,
      emptyList(),
      discrepancyPriority = discrepancyPriority,
      detail = "detail",
      action = "exaction",
      createdBy = USERNAME,
    )
    whenever(serviceUserService.getUsername()).thenReturn(USERNAME)
    whenever(comparisonPersonDiscrepancyRepository.save(any())).thenReturn(discrepancy)
    whenever(
      comparisonPersonDiscrepancyRepository.findTopByComparisonPersonShortReferenceAndSupersededByIdIsNullOrderByCreatedAtDesc(
        comparisonPerson.shortReference,
      ),
    ).thenReturn(existingDiscrepancy)

    val discrepancyCause = DiscrepancyCause(DiscrepancyCategory.TUSED, DiscrepancySubCategory.REMAND_OR_UAL_RELATED)
    val discrepancyRequest = CreateComparisonDiscrepancyRequest(
      impact = discrepancyImpact.impact,
      listOf(discrepancyCause),
      detail = discrepancy.detail,
      priority = discrepancyPriority.priority,
      action = discrepancy.action,
    )
    val discrepancySummary = comparisonDiscrepancyService.createDiscrepancy(
      comparison,
      comparisonPerson,
      discrepancyRequest,
    )
    verify(comparisonPersonDiscrepancyRepository, times(2)).save(any())
    verify(comparisonPersonDiscrepancyCategoryRepository).saveAll(ArgumentMatchers.anyList())
    assertEquals(discrepancyRequest.impact, discrepancySummary.impact)
    assertEquals(discrepancyRequest.priority, discrepancySummary.priority)
    assertEquals(discrepancyRequest.action, discrepancySummary.action)
    assertEquals(discrepancyRequest.detail, discrepancySummary.detail)
    val causes = discrepancySummary.causes
    assertEquals(1, causes.size)
    assertEquals(discrepancyCause.category, causes[0].category)
    assertEquals(discrepancyCause.subCategory, causes[0].subCategory)
  }

  private fun someReleaseDates(): MutableMap<ReleaseDateType, LocalDate> {
    val releaseDates = mutableMapOf<ReleaseDateType, LocalDate>()
    releaseDates[ReleaseDateType.SED] = LocalDate.of(2026, 1, 1)
    releaseDates[ReleaseDateType.ARD] = LocalDate.of(2026, 1, 1)
    releaseDates[ReleaseDateType.CRD] = LocalDate.of(2026, 1, 1)
    releaseDates[ReleaseDateType.NPD] = LocalDate.of(2026, 1, 1)
    releaseDates[ReleaseDateType.PRRD] = LocalDate.of(2026, 1, 1)
    releaseDates[ReleaseDateType.LED] = LocalDate.of(2026, 1, 1)
    releaseDates[ReleaseDateType.HDCED] = LocalDate.of(2026, 1, 1)
    releaseDates[ReleaseDateType.PED] = LocalDate.of(2026, 1, 1)
    releaseDates[ReleaseDateType.HDCAD] = LocalDate.of(2026, 1, 1)
    releaseDates[ReleaseDateType.APD] = LocalDate.of(2026, 1, 1)
    releaseDates[ReleaseDateType.ROTL] = LocalDate.of(2026, 1, 1)
    releaseDates[ReleaseDateType.ERSED] = LocalDate.of(2026, 1, 1)
    releaseDates[ReleaseDateType.ETD] = LocalDate.of(2026, 1, 1)
    releaseDates[ReleaseDateType.MTD] = LocalDate.of(2026, 1, 1)
    releaseDates[ReleaseDateType.LTD] = LocalDate.of(2026, 1, 1)
    releaseDates[ReleaseDateType.TUSED] = LocalDate.of(2026, 1, 1)
    releaseDates[ReleaseDateType.Tariff] = LocalDate.of(2026, 1, 1)
    releaseDates[ReleaseDateType.DPRRD] = LocalDate.of(2026, 1, 1)
    releaseDates[ReleaseDateType.TERSED] = LocalDate.of(2026, 1, 1)
    releaseDates[ReleaseDateType.ESED] = LocalDate.of(2026, 1, 1)
    return releaseDates
  }

  private fun aComparison() = Comparison(
    1,
    UUID.randomUUID(),
    "ABCD1234",
    JsonNodeFactory.instance.objectNode(),
    "ABC",
    ComparisonType.MANUAL,
    LocalDateTime.now(),
    USERNAME,
    ComparisonStatus(ComparisonStatusValue.PROCESSING),
  )

  private fun aComparisonPerson(
    comparisonId: Long,
    person: String,
  ): ComparisonPerson {
    val emptyObjectNode = objectMapper.createObjectNode()
    return ComparisonPerson(
      1,
      comparisonId,
      person = person,
      lastName = "Smith",
      latestBookingId = 25,
      isMatch = false,
      isValid = true,
      isFatal = false,
      mismatchType = MismatchType.RELEASE_DATES_MISMATCH,
      validationMessages = emptyObjectNode,
      calculatedByUsername = USERNAME,
      nomisDates = emptyObjectNode,
      overrideDates = emptyObjectNode,
      breakdownByReleaseDateType = emptyObjectNode,
      sdsPlusSentencesIdentified = emptyObjectNode,
      establishment = "BMI",
    )
  }

  companion object {
    const val USERNAME = "user1"
  }
}
