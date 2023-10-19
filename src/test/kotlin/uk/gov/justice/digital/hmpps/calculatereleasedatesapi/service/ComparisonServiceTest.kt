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
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.Comparison
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.ComparisonStatus
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ComparisonStatusValue
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.CrdWebException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Mismatch
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.ComparisonInput
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi.CalculableSentenceEnvelope
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi.Person
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.ComparisonPersonRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.ComparisonRepository
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class ComparisonServiceTest : IntegrationTestBase() {

  @InjectMocks
  lateinit var comparisonService: ComparisonService

  private val prisonService = mock<PrisonService>()
  private val comparisonRepository = mock<ComparisonRepository>()
  private val comparisonPersonRepository = mock<ComparisonPersonRepository>()
  private var serviceUserService = mock<ServiceUserService>()
  private var bulkComparisonService = mock<BulkComparisonService>()
  private val objectMapper: ObjectMapper = ObjectMapper()

  @Test
  fun `A Comparison is created when create is called`() {
    val outputComparison = Comparison(
      1,
      UUID.randomUUID(),
      "ABCD1234",
      objectMapper.createObjectNode(),
      "ABC",
      false,
      LocalDateTime.now(),
      USERNAME,
      ComparisonStatus(ComparisonStatusValue.PROCESSING),
      null,
    )

    Mockito.`when`(serviceUserService.getUsername()).thenReturn(USERNAME)
    Mockito.`when`(comparisonRepository.save(any())).thenReturn(outputComparison)

    val comparisonInput = ComparisonInput(objectMapper.createObjectNode(), prison = "ABC")
    val comparison = comparisonService.create(comparisonInput)
    Assertions.assertEquals(outputComparison, comparison)
  }

  @Test
  fun `A Comparison is created and results returned when create is called on AUTO`() {
    val outputComparison = Comparison(
      1,
      UUID.randomUUID(),
      "ABCD1234",
      objectMapper.createObjectNode(),
      "ABC",
      false,
      LocalDateTime.now(),
      USERNAME,
      ComparisonStatus(ComparisonStatusValue.PROCESSING),
      2,
    )

    val calculableSentenceEnvelope = CalculableSentenceEnvelope(
      Person("A", LocalDate.of(1990, 5, 1)),
      1,
      emptyList(),
      emptyList(),
      emptyList(),
      emptyList(),
      null,
      null,
    )

    val mismatch = Mismatch(
      true,
      true,
      calculableSentenceEnvelope,
      null,
    )

    Mockito.`when`(serviceUserService.getUsername()).thenReturn(USERNAME)
    Mockito.`when`(comparisonRepository.save(any())).thenReturn(outputComparison)

    val comparisonInput = ComparisonInput(null, prison = "ABC")
    val comparison = comparisonService.create(comparisonInput)
    Assertions.assertEquals(outputComparison, comparison)
  }

  @Test
  fun `Get a list of comparisons`() {
    Mockito.`when`(serviceUserService.getUsername()).thenReturn(USERNAME)
    Mockito.`when`(prisonService.getCurrentUserPrisonsList()).thenReturn(listOf("ABC"))
    Mockito.`when`(comparisonRepository.findAllByManualInputAndPrisonIsIn(any(), any())).thenReturn(
      listOf(
        Comparison(
          1,
          UUID.randomUUID(),
          "ABCD1234",
          objectMapper.createObjectNode(),
          "ABC",
          false,
          LocalDateTime.now(),
          USERNAME,
          ComparisonStatus(ComparisonStatusValue.PROCESSING),
          null,
        ),
      ),
    )

    val comparisonList = comparisonService.listComparisons()

    Assertions.assertEquals(comparisonList.size, 1)
  }

  @Test
  fun `Get a count of people associated with a single calculation reference with unknown reference`() {
    val numberOfPeople = comparisonService.getCountOfPersonsInComparisonByComparisonReference("ABCD1234")
    Assertions.assertEquals(0, numberOfPeople)
  }

  @Test
  fun `Get a count of people associated with a single calculation reference in a different prison`() {
    val prison = "ABC"
    Mockito.`when`(prisonService.getCurrentUserPrisonsList()).thenReturn(listOf("ADIFFERENTPRISON"))
    val comparison = Comparison(
      1,
      UUID.randomUUID(),
      "ABCD1234",
      objectMapper.createObjectNode(),
      prison,
      true,
      LocalDateTime.now(),
      USERNAME,
      ComparisonStatus(ComparisonStatusValue.PROCESSING),
      null,
    )
    Mockito.`when`(comparisonRepository.findByManualInputAndComparisonShortReference(false, "ABCD1234")).thenReturn(comparison)
    val numberOfPeople = comparisonService.getCountOfPersonsInComparisonByComparisonReference(comparison.comparisonShortReference)
    Assertions.assertEquals(0, numberOfPeople)
  }

  @Test
  fun `Get a count of people associated with a single calculation reference with valid reference`() {
    val prison = "ABC"
    Mockito.`when`(prisonService.getCurrentUserPrisonsList()).thenReturn(listOf(prison))
    Mockito.`when`(comparisonPersonRepository.countByComparisonId(1)).thenReturn(7)
    val comparison = Comparison(
      1,
      UUID.randomUUID(),
      "ABCD1234",
      objectMapper.createObjectNode(),
      prison,
      true,
      LocalDateTime.now(),
      USERNAME,
      ComparisonStatus(ComparisonStatusValue.PROCESSING),
      null,
    )
    Mockito.`when`(comparisonRepository.findByManualInputAndComparisonShortReference(false, "ABCD1234")).thenReturn(comparison)

    val numberOfPeople = comparisonService.getCountOfPersonsInComparisonByComparisonReference(comparison.comparisonShortReference)
    Assertions.assertEquals(7, numberOfPeople)
  }

  @Test
  fun `Get a comparison with unknown reference`() {
    Assertions.assertThrows(EntityNotFoundException::class.java) {
      comparisonService.getComparisonByComparisonReference("UNKNOWNREFERENCE")
    }
  }

  @Test
  fun `Get a comparison for a different prison`() {
    val prison = "ABC"
    Mockito.`when`(prisonService.getCurrentUserPrisonsList()).thenReturn(listOf("ADIFFERENTPRISON"))
    val comparison = Comparison(
      1,
      UUID.randomUUID(),
      "ABCD1234",
      objectMapper.createObjectNode(),
      prison,
      true,
      LocalDateTime.now(),
      USERNAME,
      ComparisonStatus(ComparisonStatusValue.PROCESSING),
      null,
    )
    Mockito.`when`(comparisonRepository.findByManualInputAndComparisonShortReference(false, "ABCD1234")).thenReturn(comparison)
    Assertions.assertThrows(CrdWebException::class.java) {
      comparisonService.getComparisonByComparisonReference("ABCD1234")
    }
  }

  @Test
  fun `Get a comparison with valid reference`() {
    val prison = "ABC"
    Mockito.`when`(prisonService.getCurrentUserPrisonsList()).thenReturn(listOf(prison))
    val comparison = Comparison(
      1,
      UUID.randomUUID(),
      "ABCD1234",
      objectMapper.createObjectNode(),
      prison,
      true,
      LocalDateTime.now(),
      USERNAME,
      ComparisonStatus(ComparisonStatusValue.PROCESSING),
      null,
    )
    Mockito.`when`(comparisonRepository.findByManualInputAndComparisonShortReference(false, "ABCD1234")).thenReturn(comparison)
    val result = comparisonService.getComparisonByComparisonReference("ABCD1234")
    Assertions.assertEquals(comparison, result)
  }

  companion object {
    const val USERNAME = "user1"
  }
}
