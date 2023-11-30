package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import jakarta.persistence.EntityNotFoundException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.TestUtil
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.Comparison
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.ComparisonPerson
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.ComparisonStatus
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ComparisonStatusValue
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.CrdWebException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.ComparisonInput
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.ComparisonPersonRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.ComparisonRepository
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

@ExtendWith(MockitoExtension::class)
class ComparisonServiceTest : IntegrationTestBase() {
  private val prisonService = mock<PrisonService>()
  private val comparisonRepository = mock<ComparisonRepository>()
  private val comparisonPersonRepository = mock<ComparisonPersonRepository>()
  private var serviceUserService = mock<ServiceUserService>()
  private var bulkComparisonService = mock<BulkComparisonService>()
  private val calculationTransactionalService = mock<CalculationTransactionalService>()
  private val objectMapper: ObjectMapper = TestUtil.objectMapper()

  private val comparisonService = ComparisonService(
    comparisonRepository,
    prisonService,
    serviceUserService,
    comparisonPersonRepository,
    bulkComparisonService,
    calculationTransactionalService,
    objectMapper,
  )

  @Test
  fun `A Comparison is created when create is called`() {
    val outputComparison = Comparison(
      1,
      UUID.randomUUID(),
      "ABCD1234",
      JsonNodeFactory.instance.objectNode(),
      "ABC",
      false,
      LocalDateTime.now(),
      USERNAME,
      ComparisonStatus(ComparisonStatusValue.PROCESSING),
    )

    whenever(serviceUserService.getUsername()).thenReturn(USERNAME)
    whenever(comparisonRepository.save(any())).thenReturn(outputComparison)

    val comparisonInput = ComparisonInput(JsonNodeFactory.instance.objectNode(), prison = "ABC")
    val comparison = comparisonService.create(comparisonInput)
    assertEquals(outputComparison, comparison)
  }

  @Test
  fun `A Comparison is created and results returned when create is called on AUTO`() {
    val outputComparison = Comparison(
      1,
      UUID.randomUUID(),
      "ABCD1234",
      JsonNodeFactory.instance.objectNode(),
      "ABC",
      false,
      LocalDateTime.now(),
      USERNAME,
      ComparisonStatus(ComparisonStatusValue.PROCESSING),
      2,
    )

    whenever(serviceUserService.getUsername()).thenReturn(USERNAME)
    whenever(comparisonRepository.save(any())).thenReturn(outputComparison)

    val comparisonInput = ComparisonInput(null, prison = "ABC")
    val comparison = comparisonService.create(comparisonInput)
    assertEquals(outputComparison, comparison)
  }

  @Test
  fun `Get a list of comparisons`() {
    whenever(serviceUserService.getUsername()).thenReturn(USERNAME)
    whenever(prisonService.getCurrentUserPrisonsList()).thenReturn(listOf("ABC"))
    whenever(comparisonRepository.findAllByManualInputAndPrisonIsIn(any(), any())).thenReturn(
      listOf(
        Comparison(
          1,
          UUID.randomUUID(),
          "ABCD1234",
          JsonNodeFactory.instance.objectNode(),
          "ABC",
          false,
          LocalDateTime.now(),
          USERNAME,
          ComparisonStatus(ComparisonStatusValue.PROCESSING),
        ),
      ),
    )

    val comparisonList = comparisonService.listComparisons()

    assertEquals(comparisonList.size, 1)
  }

  @Test
  fun `Get a count of people associated with a single calculation reference with unknown reference`() {
    val numberOfPeople = comparisonService.getCountOfPersonsInComparisonByComparisonReference("ABCD1234")
    assertEquals(0, numberOfPeople)
  }

  @Test
  fun `Get a count of people associated with a single calculation reference in a different prison`() {
    val prison = "ABC"
    Mockito.`when`(prisonService.getCurrentUserPrisonsList()).thenReturn(listOf("ADIFFERENTPRISON"))
    val comparison = Comparison(
      1,
      UUID.randomUUID(),
      "ABCD1234",
      JsonNodeFactory.instance.objectNode(),
      prison,
      true,
      LocalDateTime.now(),
      USERNAME,
      ComparisonStatus(ComparisonStatusValue.PROCESSING),
    )
    whenever(
      comparisonRepository.findByManualInputAndComparisonShortReference(
        false,
        "ABCD1234",
      ),
    ).thenReturn(comparison)
    val numberOfPeople =
      comparisonService.getCountOfPersonsInComparisonByComparisonReference(comparison.comparisonShortReference)
    assertEquals(0, numberOfPeople)
  }

  @Test
  fun `Get a count of people associated with a single calculation reference with valid reference`() {
    val prison = "ABC"
    whenever(prisonService.getCurrentUserPrisonsList()).thenReturn(listOf(prison))
    whenever(comparisonPersonRepository.countByComparisonId(1)).thenReturn(7)
    val comparison = Comparison(
      1,
      UUID.randomUUID(),
      "ABCD1234",
      JsonNodeFactory.instance.objectNode(),
      prison,
      true,
      LocalDateTime.now(),
      USERNAME,
      ComparisonStatus(ComparisonStatusValue.PROCESSING),
    )
    whenever(
      comparisonRepository.findByManualInputAndComparisonShortReference(
        false,
        "ABCD1234",
      ),
    ).thenReturn(comparison)

    val numberOfPeople =
      comparisonService.getCountOfPersonsInComparisonByComparisonReference(comparison.comparisonShortReference)
    assertEquals(7, numberOfPeople)
  }

  @Test
  fun `Get a comparison with unknown reference`() {
    assertThrows(EntityNotFoundException::class.java) {
      comparisonService.getComparisonByComparisonReference("UNKNOWNREFERENCE")
    }
  }

  @Test
  fun `Get a comparison for a different prison`() {
    val prison = "ABC"
    whenever(prisonService.getCurrentUserPrisonsList()).thenReturn(listOf("ADIFFERENTPRISON"))
    val comparison = Comparison(
      1,
      UUID.randomUUID(),
      "ABCD1234",
      JsonNodeFactory.instance.objectNode(),
      prison,
      true,
      LocalDateTime.now(),
      USERNAME,
      ComparisonStatus(ComparisonStatusValue.PROCESSING),
    )
    whenever(
      comparisonRepository.findByManualInputAndComparisonShortReference(
        false,
        "ABCD1234",
      ),
    ).thenReturn(comparison)
    assertThrows(CrdWebException::class.java) {
      comparisonService.getComparisonByComparisonReference("ABCD1234")
    }
  }

  @Test
  fun `Get a comparison with valid reference`() {
    val prison = "ABC"
    whenever(prisonService.getCurrentUserPrisonsList()).thenReturn(listOf(prison))
    val comparison = Comparison(
      1,
      UUID.randomUUID(),
      "ABCD1234",
      JsonNodeFactory.instance.objectNode(),
      prison,
      true,
      LocalDateTime.now(),
      USERNAME,
      ComparisonStatus(ComparisonStatusValue.PROCESSING),
    )
    whenever(
      comparisonRepository.findByManualInputAndComparisonShortReference(
        false,
        "ABCD1234",
      ),
    ).thenReturn(comparison)
    val result = comparisonService.getComparisonByComparisonReference("ABCD1234")
    assertEquals(comparison.comparisonShortReference, result.comparisonShortReference)
  }

  @Test
  fun `Sorts comparison mismatches by earliest release date`() {
    val prison = "ABC"
    whenever(prisonService.getCurrentUserPrisonsList()).thenReturn(listOf(prison))
    val comparison = Comparison(
      1,
      UUID.randomUUID(),
      "ABCD1234",
      JsonNodeFactory.instance.objectNode(),
      prison,
      true,
      LocalDateTime.now(),
      USERNAME,
      ComparisonStatus(ComparisonStatusValue.PROCESSING),
    )

    val comparisonPerson1 = aComparisonPerson(
      1,
      comparison.id,
      "person 1",
      someReleaseDates(crd = LocalDate.of(2036, 2, 20), led = LocalDate.of(2037, 2, 20)),
    )
    val comparisonPerson2 = aComparisonPerson(
      2,
      comparison.id,
      "person 2",
      someReleaseDates(ped = LocalDate.of(2032, 4, 24)),
    )
    val comparisonPerson3 = aComparisonPerson(
      3,
      comparison.id,
      "person 3",
      someReleaseDates(),
    )
    val comparisonPerson4 = aComparisonPerson(
      4,
      comparison.id,
      "person 4",
      someReleaseDates(crd = LocalDate.of(2034, 6, 15)),
    )
    val comparisonPerson5 = aComparisonPerson(
      5,
      comparison.id,
      "person 5",
      someReleaseDates(crd = LocalDate.of(2028, 6, 19), hdced = LocalDate.of(2028, 3, 31)),
    )
    val comparisonPerson6 = aComparisonPerson(
      6,
      comparison.id,
      "person 6",
      someReleaseDates(),
    )

    val comparisonPersons =
      listOf(
        comparisonPerson1,
        comparisonPerson2,
        comparisonPerson3,
        comparisonPerson4,
        comparisonPerson5,
        comparisonPerson6,
      )
    whenever(
      comparisonRepository.findByManualInputAndComparisonShortReference(
        false,
        "ABCD1234",
      ),
    ).thenReturn(comparison)
    whenever(comparisonPersonRepository.findByComparisonIdIs(comparison.id)).thenReturn(comparisonPersons)

    val result = comparisonService.getComparisonByComparisonReference("ABCD1234")

    assertEquals(comparison.comparisonShortReference, result.comparisonShortReference)
    assertEquals(comparisonPersons.size, result.mismatches.size)
    assertEquals(comparisonPerson5.person, result.mismatches[0].personId)
    assertEquals(comparisonPerson2.person, result.mismatches[1].personId)
    assertEquals(comparisonPerson4.person, result.mismatches[2].personId)
    assertEquals(comparisonPerson1.person, result.mismatches[3].personId)
    assertEquals(comparisonPerson3.person, result.mismatches[4].personId)
    assertEquals(comparisonPerson6.person, result.mismatches[5].personId)
  }

  private fun aComparisonPerson(
    id: Long,
    comparisonId: Long,
    person: String,
    releaseDates: Map<ReleaseDateType, LocalDate?>,
  ): ComparisonPerson {
    val emptyObjectNode = objectMapper.createObjectNode()
    return ComparisonPerson(
      id,
      comparisonId,
      person = person,
      latestBookingId = 25,
      isMatch = false,
      isValid = true,
      validationMessages = emptyObjectNode,
      calculatedByUsername = USERNAME,
      nomisDates = objectMapper.valueToTree(releaseDates),
      overrideDates = emptyObjectNode,
      breakdownByReleaseDateType = emptyObjectNode,
    )
  }

  private fun someReleaseDates(
    crd: LocalDate? = null,
    ped: LocalDate? = null,
    led: LocalDate? = null,
    hdced: LocalDate? = null,
  ): Map<ReleaseDateType, LocalDate?> {
    return mapOf(
      ReleaseDateType.APD to null,
      ReleaseDateType.ARD to null,
      ReleaseDateType.CRD to crd,
      ReleaseDateType.ETD to null,
      ReleaseDateType.LED to led,
      ReleaseDateType.LTD to null,
      ReleaseDateType.MTD to null,
      ReleaseDateType.NPD to null,
      ReleaseDateType.PED to ped,
      ReleaseDateType.SED to null,
      ReleaseDateType.ESED to null,
      ReleaseDateType.PRRD to null,
      ReleaseDateType.ROTL to null,
      ReleaseDateType.DPRRD to null,
      ReleaseDateType.ERSED to null,
      ReleaseDateType.HDCAD to null,
      ReleaseDateType.HDCED to hdced,
      ReleaseDateType.TUSED to null,
      ReleaseDateType.TERSED to null,
      ReleaseDateType.Tariff to null,
    )
  }

  companion object {
    const val USERNAME = "user1"
  }
}
