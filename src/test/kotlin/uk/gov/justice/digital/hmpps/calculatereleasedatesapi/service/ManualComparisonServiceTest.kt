package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.persistence.EntityNotFoundException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions
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
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ComparisonType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.manualComparisonTypes
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ComparisonPersonOverview
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ManualComparisonInput
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.MismatchType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SDSEarlyReleaseExclusionType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceAndOffenceWithReleaseArrangements
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.OffenderOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceTerms
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.ComparisonPersonDiscrepancyRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.ComparisonPersonRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.ComparisonRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationMessage
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

@ExtendWith(MockitoExtension::class)
class ManualComparisonServiceTest {

  private val objectMapper: ObjectMapper = TestUtil.objectMapper()
  private val comparisonRepository = mock<ComparisonRepository>()
  private val comparisonPersonRepository = mock<ComparisonPersonRepository>()
  private val comparisonPersonDiscrepancyRepository = mock<ComparisonPersonDiscrepancyRepository>()
  private var serviceUserService = mock<ServiceUserService>()
  private var bulkComparisonService = mock<BulkComparisonService>()
  private val calculationTransactionalService = mock<CalculationTransactionalService>()
  private val manualComparisonService: ManualComparisonService = ManualComparisonService(
    comparisonRepository,
    serviceUserService,
    comparisonPersonRepository,
    comparisonPersonDiscrepancyRepository,
    bulkComparisonService,
    objectMapper,
    calculationTransactionalService,
  )

  @Test
  fun `A Comparison is created when create is called`() {
    val outputComparison = Comparison(
      1,
      UUID.randomUUID(),
      "ABCD1234",
      objectMapper.createObjectNode(),
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
      objectMapper.createObjectNode(),
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
      objectMapper.createObjectNode(),
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
      objectMapper.createObjectNode(),
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

  @Test
  fun `get a comparison person with SDS+ sentences`() {
    val emptyObjectNode = objectMapper.createObjectNode()
    val sdsPlusSentence = SentenceAndOffenceWithReleaseArrangements(
      bookingId = 1L,
      sentenceSequence = 3,
      consecutiveToSequence = null,
      lineSequence = 2,
      caseSequence = 1,
      sentenceDate = ImportantDates.PCSC_COMMENCEMENT_DATE.minusDays(1),
      terms = listOf(
        SentenceTerms(years = 8),
      ),
      sentenceStatus = "IMP",
      sentenceCategory = "CAT",
      sentenceCalculationType = SentenceCalculationType.ADIMP.name,
      sentenceTypeDescription = "ADMIP",
      offence = OffenderOffence(1L, LocalDate.of(2015, 1, 1), null, "ADIMP_ORA", "description", listOf("A")),
      caseReference = null,
      courtDescription = null,
      fineAmount = null,
      isSDSPlus = true,
      hasAnSDSEarlyReleaseExclusion = SDSEarlyReleaseExclusionType.NO,
    )
    val person = ComparisonPerson(
      1,
      1,
      person = "foo",
      lastName = "Smith",
      latestBookingId = 25,
      isMatch = false,
      isValid = true,
      mismatchType = MismatchType.RELEASE_DATES_MISMATCH,
      validationMessages = objectMapper.valueToTree(emptyList<ValidationMessage>()),
      calculatedByUsername = BulkComparisonServiceTest.USERNAME,
      nomisDates = emptyObjectNode,
      overrideDates = emptyObjectNode,
      breakdownByReleaseDateType = emptyObjectNode,
      sdsPlusSentencesIdentified = objectMapper.valueToTree(listOf(sdsPlusSentence)),
      establishment = "BMI",
    )
    val comparison = Comparison(
      calculatedByUsername = USERNAME,
      comparisonStatus = ComparisonStatus(ComparisonStatusValue.COMPLETED),
      comparisonType = ComparisonType.MANUAL,
      criteria = emptyObjectNode,
    )
    whenever(comparisonRepository.findByComparisonShortReference(any())).thenReturn(comparison)
    whenever(comparisonPersonRepository.findByComparisonIdAndShortReference(any(), any())).thenReturn(person)
    whenever(comparisonPersonDiscrepancyRepository.existsByComparisonPerson(any())).thenReturn(false)
    val result = manualComparisonService.getComparisonPersonByShortReference("ABCD1234", "FOO")
    assertThat(result).isEqualTo(
      ComparisonPersonOverview(
        personId = "foo",
        lastName = "Smith",
        isValid = true,
        isMatch = false,
        hasDiscrepancyRecord = false,
        mismatchType = MismatchType.RELEASE_DATES_MISMATCH,
        isActiveSexOffender = null,
        validationMessages = emptyList(),
        shortReference = person.shortReference,
        bookingId = 25,
        calculatedAt = person.calculatedAt,
        crdsDates = emptyMap(),
        nomisDates = emptyMap(),
        overrideDates = emptyMap(),
        breakdownByReleaseDateType = emptyMap(),
        sdsSentencesIdentified = listOf(sdsPlusSentence),
      ),
    )
  }

  companion object {
    const val USERNAME = "user1"
  }
}
