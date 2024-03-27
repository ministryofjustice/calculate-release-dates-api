package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.persistence.EntityNotFoundException
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.Comparison
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.ComparisonStatus
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ComparisonStatusValue
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ComparisonType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.manualComparisonTypes
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ManualComparisonInput
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.ComparisonPersonDiscrepancyRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.ComparisonPersonRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.ComparisonRepository
import java.time.LocalDateTime
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class ManualComparisonServiceTest : IntegrationTestBase() {

  @InjectMocks
  lateinit var manualComparisonService: ManualComparisonService

  @Autowired
  var spyMapper: ObjectMapper = spy<ObjectMapper>()

  private val comparisonRepository = mock<ComparisonRepository>()
  private val comparisonPersonRepository = mock<ComparisonPersonRepository>()
  private val comparisonPersonDiscrepancyRepository = mock<ComparisonPersonDiscrepancyRepository>()
  private var serviceUserService = mock<ServiceUserService>()
  private var bulkComparisonService = mock<BulkComparisonService>()
  private val calculationTransactionalService = mock<CalculationTransactionalService>()

  @Test
  fun `A Comparison is created when create is called`() {
    val outputComparison = Comparison(
      1,
      UUID.randomUUID(),
      "ABCD1234",
      spyMapper.createObjectNode(),
      null,
      ComparisonType.MANUAL,
      LocalDateTime.now(),
      USERNAME,
      ComparisonStatus(ComparisonStatusValue.PROCESSING),
    )

    Mockito.`when`(serviceUserService.getUsername()).thenReturn(USERNAME)
    Mockito.`when`(comparisonRepository.save(any())).thenReturn(outputComparison)

    val comparisonInput = ManualComparisonInput(listOf("ABC123"))
    val comparison = manualComparisonService.create(comparisonInput, "")
    Assertions.assertEquals(outputComparison, comparison)
  }

  @Test
  fun `Get a list of manual comparisons`() {
    Mockito.`when`(serviceUserService.getUsername()).thenReturn(USERNAME)
    val comparison = Comparison(
      1,
      UUID.randomUUID(),
      "ABCD1234",
      spyMapper.createObjectNode(),
      "ABC",
      ComparisonType.MANUAL,
      LocalDateTime.now(),
      USERNAME,
      ComparisonStatus(ComparisonStatusValue.PROCESSING),
    )
    Mockito.`when`(comparisonRepository.findAllByComparisonTypeIsIn(manualComparisonTypes())).thenReturn(listOf(comparison))

    val manualComparisonList = manualComparisonService.listManual()
    Assertions.assertTrue(manualComparisonList.isNotEmpty())
  }

  @Test
  fun `Get a count of people associated with a single calculation reference with unknown reference`() {
    val numberOfPeople = manualComparisonService.getCountOfPersonsInComparisonByComparisonReference("ABCD1234")
    Assertions.assertEquals(0, numberOfPeople)
  }

  @Test
  fun `Get a count of people associated with a single calculation reference with valid reference`() {
    Mockito.`when`(comparisonPersonRepository.countByComparisonId(1)).thenReturn(7)
    val comparison = Comparison(
      1,
      UUID.randomUUID(),
      "ABCD1234",
      spyMapper.createObjectNode(),
      null,
      ComparisonType.MANUAL,
      LocalDateTime.now(),
      USERNAME,
      ComparisonStatus(ComparisonStatusValue.PROCESSING),
    )
    Mockito.`when`(comparisonRepository.findByComparisonShortReference("ABCD1234")).thenReturn(comparison)

    val numberOfPeople = manualComparisonService.getCountOfPersonsInComparisonByComparisonReference(comparison.comparisonShortReference)
    Assertions.assertEquals(7, numberOfPeople)
  }

  @Test
  fun `Get a comparison with unknown reference`() {
    Assertions.assertThrows(EntityNotFoundException::class.java) {
      manualComparisonService.getComparisonByComparisonReference("UNKNOWNREFERENCE")
    }
  }

  @Test
  fun `Get a comparison with valid reference`() {
    val comparison = Comparison(
      1,
      UUID.randomUUID(),
      "ABCD1234",
      spyMapper.createObjectNode(),
      null,
      ComparisonType.MANUAL,
      LocalDateTime.now(),
      USERNAME,
      ComparisonStatus(ComparisonStatusValue.PROCESSING),
    )
    Mockito.`when`(comparisonRepository.findByComparisonShortReference("ABCD1234")).thenReturn(comparison)
    val result = manualComparisonService.getComparisonByComparisonReference("ABCD1234")
    Assertions.assertEquals(comparison.comparisonShortReference, result.comparisonShortReference)
  }

  companion object {
    const val USERNAME = "user1"
  }
}
