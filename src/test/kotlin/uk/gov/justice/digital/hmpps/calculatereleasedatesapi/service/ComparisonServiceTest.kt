package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import com.fasterxml.jackson.databind.node.JsonNodeFactory
import jakarta.persistence.EntityNotFoundException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.TestUtil
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationOutcome
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
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.CrdWebException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Agency
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ComparisonDiscrepancySummary
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ComparisonPersonOverview
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CreateComparisonDiscrepancyRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.DiscrepancyCause
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.MismatchType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ReleaseDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SDSEarlyReleaseExclusionType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceAndOffenceWithReleaseArrangements
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.ComparisonInput
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.OffenderOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceTerms
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationOutcomeRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.ComparisonPersonDiscrepancyRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.ComparisonPersonRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.ComparisonRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationMessage
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class ComparisonServiceTest {
  private val objectMapper = TestUtil.objectMapper()
  private val prisonService = mock<PrisonService>()
  private val calculationOutcomeRepository = mock<CalculationOutcomeRepository>()
  private val comparisonRepository = mock<ComparisonRepository>()
  private val comparisonPersonRepository = mock<ComparisonPersonRepository>()
  private val comparisonPersonDiscrepancyRepository = mock<ComparisonPersonDiscrepancyRepository>()
  private var serviceUserService = mock<ServiceUserService>()
  private var bulkComparisonService = mock<BulkComparisonService>()
  private val calculationTransactionalService = mock<CalculationTransactionalService>()

  private val comparisonService = ComparisonService(
    calculationOutcomeRepository,
    comparisonRepository,
    prisonService,
    serviceUserService,
    comparisonPersonRepository,
    comparisonPersonDiscrepancyRepository,
    bulkComparisonService,
    calculationTransactionalService,
    objectMapper,
  )

  @BeforeEach
  fun beforeAll() {
    whenever(serviceUserService.getUsername()).thenReturn(USERNAME)
  }

  @Test
  fun `A Comparison is created when create is called`() {
    val outputComparison = aComparison()
    whenever(comparisonRepository.save(any())).thenReturn(outputComparison)

    val comparisonInput = ComparisonInput(JsonNodeFactory.instance.objectNode(), prison = "ABC")
    val comparison = comparisonService.create(comparisonInput, "")
    assertEquals(outputComparison, comparison)
  }

  @Test
  fun `A Comparison is created and results returned when create is called on AUTO`() {
    val outputComparison = aComparison()
    whenever(comparisonRepository.save(any())).thenReturn(outputComparison)

    val comparisonInput = ComparisonInput(null, prison = "ABC")
    val comparison = comparisonService.create(comparisonInput, "")
    assertEquals(outputComparison, comparison)
  }

  @Test
  fun `Get a list of comparisons`() {
    whenever(prisonService.getCurrentUserPrisonsList()).thenReturn(listOf("ABC"))
    whenever(comparisonRepository.findAllByComparisonTypeIsInAndPrisonIsIn(any(), any())).thenReturn(
      listOf(
        Comparison(
          1,
          UUID.randomUUID(),
          "ABCD1234",
          JsonNodeFactory.instance.objectNode(),
          "ABC",
          ComparisonType.ESTABLISHMENT_FULL,
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
    whenever(prisonService.getCurrentUserPrisonsList()).thenReturn(listOf("ADIFFERENTPRISON"))
    val comparison = aComparison()

    whenever(
      comparisonRepository.findByComparisonShortReference("ABCD1234"),
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

    val comparison = aComparison()
    whenever(
      comparisonRepository.findByComparisonShortReference("ABCD1234"),
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
    whenever(prisonService.getCurrentUserPrisonsList()).thenReturn(listOf("ADIFFERENTPRISON"))
    val comparison = aComparison()
    whenever(
      comparisonRepository.findByComparisonShortReference("ABCD1234"),
    ).thenReturn(comparison)

    assertThrows(CrdWebException::class.java) {
      comparisonService.getComparisonByComparisonReference("ABCD1234")
    }
  }

  @Test
  fun `Get a comparison with valid reference`() {
    whenever(prisonService.getCurrentUserPrisonsList()).thenReturn(listOf("ABC"))
    val comparison = aComparison()
    whenever(
      comparisonRepository.findByComparisonShortReference("ABCD1234"),
    ).thenReturn(comparison)

    val result = comparisonService.getComparisonByComparisonReference("ABCD1234")
    assertEquals(comparison.comparisonShortReference, result.comparisonShortReference)
  }

  @Test
  fun `Sorts comparison mismatches by establishment and release date`() {
    whenever(prisonService.getCurrentUserPrisonsList()).thenReturn(listOf("ABC"))
    val comparison = aComparison()
    val comparisonPerson1 = aComparisonPerson(
      1,
      comparison.id,
      1,
      "person 1",
      establishment = "BMI",
    )
    val calculationOutcomePerson1Crd = CalculationOutcome(
      calculationDateType = ReleaseDateType.CRD.name,
      outcomeDate = LocalDate.of(2036, 2, 20),
      calculationRequestId = 1,
    )
    val calculationOutcomePerson1Esed = CalculationOutcome(
      calculationDateType = ReleaseDateType.ESED.name,
      outcomeDate = LocalDate.of(2024, 5, 13),
      calculationRequestId = 1,
    )
    val comparisonPerson2 = aComparisonPerson(
      2,
      comparison.id,
      2,
      "person 2",
      establishment = "BMI",
    )
    val calculationOutcomePerson2Ard = CalculationOutcome(
      calculationDateType = ReleaseDateType.ARD.name,
      outcomeDate = LocalDate.of(2032, 4, 24),
      calculationRequestId = 2,
    )
    val comparisonPerson3 = aComparisonPerson(
      3,
      comparison.id,
      3,
      "person 3",
      establishment = "AYI",
    )
    val comparisonPerson4 = aComparisonPerson(
      4,
      comparison.id,
      4,
      "person 4",
      establishment = "BMI",
    )
    val calculationOutcomePerson4Crd = CalculationOutcome(
      calculationDateType = ReleaseDateType.CRD.name,
      outcomeDate = LocalDate.of(2034, 6, 15),
      calculationRequestId = 4,
    )

    val comparisonPerson5 = aComparisonPerson(
      5,
      comparison.id,
      5,
      "person 5",
      establishment = "BMI",
    )
    val calculationOutcomePerson5Crd = CalculationOutcome(
      calculationDateType = ReleaseDateType.CRD.name,
      outcomeDate = LocalDate.of(2028, 6, 19),
      calculationRequestId = 5,
    )
    val calculationOutcomePerson5Hdced = CalculationOutcome(
      calculationDateType = ReleaseDateType.HDCED.name,
      outcomeDate = LocalDate.of(2028, 6, 19),
      calculationRequestId = 5,
    )

    val comparisonPerson6 = aComparisonPerson(
      6,
      comparison.id,
      6,
      "person 6",
      establishment = "DAI",
    )
    val calculationOutcomePerson6Prrd = CalculationOutcome(
      calculationDateType = ReleaseDateType.PRRD.name,
      outcomeDate = LocalDate.of(2029, 6, 15),
      calculationRequestId = 6,
    )
    val comparisonPerson7 = aComparisonPerson(
      7,
      comparison.id,
      7,
      "person 7",
      establishment = "AYI",
    )
    val calculationOutcomePerson7Mtd = CalculationOutcome(
      calculationDateType = ReleaseDateType.MTD.name,
      outcomeDate = LocalDate.of(2033, 4, 25),
      calculationRequestId = 7,
    )
    val comparisonPersons = listOf(
      comparisonPerson1,
      comparisonPerson2,
      comparisonPerson3,
      comparisonPerson4,
      comparisonPerson5,
      comparisonPerson6,
      comparisonPerson7,
    )
    val calculationOutcomes = listOf(
      calculationOutcomePerson2Ard,
      calculationOutcomePerson6Prrd,
      calculationOutcomePerson1Crd,
      calculationOutcomePerson4Crd,
      calculationOutcomePerson5Crd,
      calculationOutcomePerson1Esed,
      calculationOutcomePerson5Hdced,
      calculationOutcomePerson7Mtd,
    )
    whenever(
      comparisonRepository.findByComparisonShortReference("ABCD1234"),
    ).thenReturn(comparison)
    whenever(comparisonPersonRepository.findByComparisonIdIsAndIsMatchFalse(comparison.id)).thenReturn(comparisonPersons)

    whenever(calculationOutcomeRepository.findForComparisonAndReleaseDatesMismatch(any())).thenReturn(calculationOutcomes)
    whenever(prisonService.getAgenciesByType("INST")).thenReturn(listOf(Agency("AYI", "Aylesbury (HMP)"), Agency("BMI", "Birmingham (HMP)"), Agency("DAI", "Dartmoor (HMP)")))

    val result = comparisonService.getComparisonByComparisonReference("ABCD1234")

    assertEquals(comparison.comparisonShortReference, result.comparisonShortReference)
    assertEquals(comparisonPersons.size, result.mismatches.size)
    assertEquals(comparisonPerson7.person, result.mismatches[0].personId)
    assertEquals(comparisonPerson3.person, result.mismatches[1].personId)
    assertEquals(comparisonPerson5.person, result.mismatches[2].personId)
    assertEquals(comparisonPerson2.person, result.mismatches[3].personId)
    assertEquals(comparisonPerson4.person, result.mismatches[4].personId)
    assertEquals(comparisonPerson1.person, result.mismatches[5].personId)
    assertEquals(comparisonPerson6.person, result.mismatches[6].personId)
  }

  @Test
  fun `Sorts comparison mismatches by earliest release date if establishment not populated`() {
    whenever(prisonService.getCurrentUserPrisonsList()).thenReturn(listOf("ABC"))
    val comparison = aComparison()
    val comparisonPerson1 = aComparisonPerson(
      1,
      comparison.id,
      1,
      "person 1",
    )
    val calculationOutcomePerson1Crd = CalculationOutcome(
      calculationDateType = ReleaseDateType.CRD.name,
      outcomeDate = LocalDate.of(2036, 2, 20),
      calculationRequestId = 1,
    )
    val calculationOutcomePerson1Esed = CalculationOutcome(
      calculationDateType = ReleaseDateType.ESED.name,
      outcomeDate = LocalDate.of(2024, 5, 13),
      calculationRequestId = 1,
    )
    val comparisonPerson2 = aComparisonPerson(
      2,
      comparison.id,
      2,
      "person 2",
    )
    val calculationOutcomePerson2Ard = CalculationOutcome(
      calculationDateType = ReleaseDateType.ARD.name,
      outcomeDate = LocalDate.of(2032, 4, 24),
      calculationRequestId = 2,
    )
    val comparisonPerson3 = aComparisonPerson(
      3,
      comparison.id,
      3,
      "person 3",
    )
    val comparisonPerson4 = aComparisonPerson(
      4,
      comparison.id,
      4,
      "person 4",
    )
    val calculationOutcomePerson4Crd = CalculationOutcome(
      calculationDateType = ReleaseDateType.CRD.name,
      outcomeDate = LocalDate.of(2034, 6, 15),
      calculationRequestId = 4,
    )

    val comparisonPerson5 = aComparisonPerson(
      5,
      comparison.id,
      5,
      "person 5",
    )
    val calculationOutcomePerson5Crd = CalculationOutcome(
      calculationDateType = ReleaseDateType.CRD.name,
      outcomeDate = LocalDate.of(2028, 6, 19),
      calculationRequestId = 5,
    )
    val calculationOutcomePerson5Hdced = CalculationOutcome(
      calculationDateType = ReleaseDateType.HDCED.name,
      outcomeDate = LocalDate.of(2028, 6, 19),
      calculationRequestId = 5,
    )

    val comparisonPerson6 = aComparisonPerson(
      6,
      comparison.id,
      6,
      "person 6",
    )
    val calculationOutcomePerson6Prrd = CalculationOutcome(
      calculationDateType = ReleaseDateType.PRRD.name,
      outcomeDate = LocalDate.of(2029, 6, 15),
      calculationRequestId = 6,
    )
    val comparisonPerson7 = aComparisonPerson(
      7,
      comparison.id,
      7,
      "person 7",
    )
    val calculationOutcomePerson7Mtd = CalculationOutcome(
      calculationDateType = ReleaseDateType.MTD.name,
      outcomeDate = LocalDate.of(2033, 4, 25),
      calculationRequestId = 7,
    )
    val comparisonPersons = listOf(
      comparisonPerson1,
      comparisonPerson2,
      comparisonPerson3,
      comparisonPerson4,
      comparisonPerson5,
      comparisonPerson6,
      comparisonPerson7,
    )
    val calculationOutcomes = listOf(
      calculationOutcomePerson2Ard,
      calculationOutcomePerson6Prrd,
      calculationOutcomePerson1Crd,
      calculationOutcomePerson4Crd,
      calculationOutcomePerson5Crd,
      calculationOutcomePerson1Esed,
      calculationOutcomePerson5Hdced,
      calculationOutcomePerson7Mtd,
    )
    whenever(
      comparisonRepository.findByComparisonShortReference("ABCD1234"),
    ).thenReturn(comparison)
    whenever(comparisonPersonRepository.findByComparisonIdIsAndIsMatchFalse(comparison.id)).thenReturn(comparisonPersons)

    whenever(calculationOutcomeRepository.findForComparisonAndReleaseDatesMismatch(any())).thenReturn(calculationOutcomes)

    val result = comparisonService.getComparisonByComparisonReference("ABCD1234")

    assertEquals(comparison.comparisonShortReference, result.comparisonShortReference)
    assertEquals(comparisonPersons.size, result.mismatches.size)
    assertEquals(comparisonPerson5.person, result.mismatches[0].personId)
    assertEquals(comparisonPerson6.person, result.mismatches[1].personId)
    assertEquals(comparisonPerson2.person, result.mismatches[2].personId)
    assertEquals(comparisonPerson7.person, result.mismatches[3].personId)
    assertEquals(comparisonPerson4.person, result.mismatches[4].personId)
    assertEquals(comparisonPerson1.person, result.mismatches[5].personId)
    assertEquals(comparisonPerson3.person, result.mismatches[6].personId)
  }

  @Test
  fun `Returns CRD for HDC4+ mismatches as release date if the prisoner has one`() {
    whenever(prisonService.getCurrentUserPrisonsList()).thenReturn(listOf("ABC"))
    val comparison = aComparison()
    val comparisonPerson1WithCRD = aComparisonPerson(
      1,
      comparison.id,
      1,
      "person 1",
      hdcedFourPlusDate = LocalDate.of(2028, 6, 19),
    )
    val calculationOutcomeCRD = CalculationOutcome(
      calculationDateType = ReleaseDateType.CRD.name,
      outcomeDate = LocalDate.of(2036, 2, 20),
      calculationRequestId = 1,
    )

    val comparisonPersons = listOf(comparisonPerson1WithCRD)
    val calculationOutcomes = listOf(calculationOutcomeCRD)
    whenever(comparisonRepository.findByComparisonShortReference("ABCD1234")).thenReturn(comparison)
    whenever(comparisonPersonRepository.findByComparisonIdIsAndIsMatchFalse(comparison.id)).thenReturn(emptyList())
    whenever(comparisonPersonRepository.findByComparisonIdIsAndHdcedFourPlusDateIsNotNull(comparison.id)).thenReturn(comparisonPersons)
    whenever(calculationOutcomeRepository.findForComparisonAndReleaseDatesMismatch(any())).thenReturn(calculationOutcomes)
    whenever(calculationOutcomeRepository.findForComparisonAndHdcedFourPlusDateIsNotNull(any())).thenReturn(calculationOutcomes)

    val result = comparisonService.getComparisonByComparisonReference("ABCD1234")

    assertEquals(1, result.hdc4PlusCalculated.size)
    assertEquals(ReleaseDate(LocalDate.of(2036, 2, 20), ReleaseDateType.CRD), result.hdc4PlusCalculated[0].releaseDate)
  }

  @Test
  fun `Returns ARD for HDC4+ mismatches as release date if the prisoner has one`() {
    whenever(prisonService.getCurrentUserPrisonsList()).thenReturn(listOf("ABC"))
    val comparison = aComparison()
    val comparisonPersonWithARD = aComparisonPerson(
      1,
      comparison.id,
      1,
      "person 1",
      hdcedFourPlusDate = LocalDate.of(2028, 6, 19),
    )
    val calculationOutcomeARD = CalculationOutcome(
      calculationDateType = ReleaseDateType.ARD.name,
      outcomeDate = LocalDate.of(2036, 2, 21),
      calculationRequestId = 1,
    )

    val comparisonPersons = listOf(comparisonPersonWithARD)
    val calculationOutcomes = listOf(calculationOutcomeARD)
    whenever(comparisonRepository.findByComparisonShortReference("ABCD1234")).thenReturn(comparison)
    whenever(comparisonPersonRepository.findByComparisonIdIsAndIsMatchFalse(comparison.id)).thenReturn(emptyList())
    whenever(comparisonPersonRepository.findByComparisonIdIsAndHdcedFourPlusDateIsNotNull(comparison.id)).thenReturn(comparisonPersons)
    whenever(calculationOutcomeRepository.findForComparisonAndReleaseDatesMismatch(any())).thenReturn(calculationOutcomes)
    whenever(calculationOutcomeRepository.findForComparisonAndHdcedFourPlusDateIsNotNull(any())).thenReturn(calculationOutcomes)

    val result = comparisonService.getComparisonByComparisonReference("ABCD1234")

    assertEquals(1, result.hdc4PlusCalculated.size)
    assertEquals(ReleaseDate(LocalDate.of(2036, 2, 21), ReleaseDateType.ARD), result.hdc4PlusCalculated[0].releaseDate)
  }

  @Test
  fun `Returns latest of ARD or CRD for HDC4+ mismatches as release date if the prisoner has both`() {
    whenever(prisonService.getCurrentUserPrisonsList()).thenReturn(listOf("ABC"))
    val comparison = aComparison()
    val comparisonPersonWithARD = aComparisonPerson(
      1,
      comparison.id,
      1,
      "person 1",
      hdcedFourPlusDate = LocalDate.of(2028, 6, 19),
    )
    val calculationOutcomeARD = CalculationOutcome(
      calculationDateType = ReleaseDateType.ARD.name,
      outcomeDate = LocalDate.of(2036, 2, 20),
      calculationRequestId = 1,
    )
    val calculationOutcomeCRD = CalculationOutcome(
      calculationDateType = ReleaseDateType.CRD.name,
      outcomeDate = LocalDate.of(2036, 2, 21),
      calculationRequestId = 1,
    )

    val comparisonPersons = listOf(comparisonPersonWithARD)
    val calculationOutcomes = listOf(calculationOutcomeARD, calculationOutcomeCRD)
    whenever(comparisonRepository.findByComparisonShortReference("ABCD1234")).thenReturn(comparison)
    whenever(comparisonPersonRepository.findByComparisonIdIsAndIsMatchFalse(comparison.id)).thenReturn(emptyList())
    whenever(comparisonPersonRepository.findByComparisonIdIsAndHdcedFourPlusDateIsNotNull(comparison.id)).thenReturn(comparisonPersons)
    whenever(calculationOutcomeRepository.findForComparisonAndReleaseDatesMismatch(any())).thenReturn(calculationOutcomes)
    whenever(calculationOutcomeRepository.findForComparisonAndHdcedFourPlusDateIsNotNull(any())).thenReturn(calculationOutcomes)

    val result = comparisonService.getComparisonByComparisonReference("ABCD1234")

    assertEquals(1, result.hdc4PlusCalculated.size)
    assertEquals(ReleaseDate(LocalDate.of(2036, 2, 21), ReleaseDateType.CRD), result.hdc4PlusCalculated[0].releaseDate)
  }

  @Test
  fun `Returns no release date for HDC4+ mismatches as release date if the prisoner has neither ARD or CRD (not expected if HDC4+ was calculated)`() {
    whenever(prisonService.getCurrentUserPrisonsList()).thenReturn(listOf("ABC"))
    val comparison = aComparison()
    val comparisonPersonWithARD = aComparisonPerson(
      1,
      comparison.id,
      1,
      "person 1",
      hdcedFourPlusDate = LocalDate.of(2028, 6, 19),
    )

    val comparisonPersons = listOf(comparisonPersonWithARD)
    whenever(comparisonRepository.findByComparisonShortReference("ABCD1234")).thenReturn(comparison)
    whenever(comparisonPersonRepository.findByComparisonIdIsAndIsMatchFalse(comparison.id)).thenReturn(emptyList())
    whenever(comparisonPersonRepository.findByComparisonIdIsAndHdcedFourPlusDateIsNotNull(comparison.id)).thenReturn(comparisonPersons)
    whenever(calculationOutcomeRepository.findForComparisonAndReleaseDatesMismatch(any())).thenReturn(emptyList())

    val result = comparisonService.getComparisonByComparisonReference("ABCD1234")

    assertEquals(1, result.hdc4PlusCalculated.size)
    assertNull(result.hdc4PlusCalculated[0].releaseDate)
  }

  @Test
  fun `Matches the date to the person`() {
    whenever(prisonService.getCurrentUserPrisonsList()).thenReturn(listOf("ABC"))
    val comparison = aComparison()
    val comparisonPersonWithARD = aComparisonPerson(
      1,
      comparison.id,
      1,
      "person 1",
      hdcedFourPlusDate = LocalDate.of(2028, 6, 19),
    )
    val comparisonPersonWithCRD = aComparisonPerson(
      2,
      comparison.id,
      2,
      "person 2",
      hdcedFourPlusDate = LocalDate.of(2028, 6, 19),
    )
    val calculationOutcomeARD = CalculationOutcome(
      calculationDateType = ReleaseDateType.ARD.name,
      outcomeDate = LocalDate.of(2036, 2, 20),
      calculationRequestId = 1,
    )
    val calculationOutcomeCRD = CalculationOutcome(
      calculationDateType = ReleaseDateType.CRD.name,
      outcomeDate = LocalDate.of(2036, 2, 21),
      calculationRequestId = 2,
    )

    val comparisonPersons = listOf(comparisonPersonWithARD, comparisonPersonWithCRD)
    val calculationOutcomes = listOf(calculationOutcomeARD, calculationOutcomeCRD)
    whenever(comparisonRepository.findByComparisonShortReference("ABCD1234")).thenReturn(comparison)
    whenever(comparisonPersonRepository.findByComparisonIdIsAndIsMatchFalse(comparison.id)).thenReturn(emptyList())
    whenever(comparisonPersonRepository.findByComparisonIdIsAndHdcedFourPlusDateIsNotNull(comparison.id)).thenReturn(comparisonPersons)
    whenever(calculationOutcomeRepository.findForComparisonAndReleaseDatesMismatch(any())).thenReturn(calculationOutcomes)
    whenever(calculationOutcomeRepository.findForComparisonAndHdcedFourPlusDateIsNotNull(any())).thenReturn(calculationOutcomes)

    val result = comparisonService.getComparisonByComparisonReference("ABCD1234")

    assertEquals(2, result.hdc4PlusCalculated.size)
    assertEquals(ReleaseDate(LocalDate.of(2036, 2, 20), ReleaseDateType.ARD), result.hdc4PlusCalculated[0].releaseDate)
    assertEquals(ReleaseDate(LocalDate.of(2036, 2, 21), ReleaseDateType.CRD), result.hdc4PlusCalculated[1].releaseDate)
  }

  @Test
  fun `Creates a comparison person discrepancy`() {
    val comparison = aComparison()
    val comparisonPerson = aComparisonPerson(
      54,
      comparison.id,
      8923,
      USERNAME,
      establishment = "ABC",
    )

    val discrepancyImpact = ComparisonPersonDiscrepancyImpact(DiscrepancyImpact.POTENTIAL_UNLAWFUL_DETENTION)
    val discrepancyPriority = ComparisonPersonDiscrepancyPriority(DiscrepancyPriority.MEDIUM_RISK)
    val discrepancySummary = ComparisonDiscrepancySummary(
      impact = DiscrepancyImpact.OTHER,
      causes = emptyList(),
      detail = "detail",
      priority = DiscrepancyPriority.HIGH_RISK,
      action = "action",
    )
    val discrepancy = ComparisonPersonDiscrepancy(1, comparisonPerson, discrepancyImpact, emptyList(), discrepancyPriority = discrepancyPriority, detail = "detail", action = "action", createdBy = USERNAME)
    whenever(
      comparisonRepository.findByComparisonShortReference(comparison.comparisonShortReference),
    ).thenReturn(comparison)
    whenever(
      comparisonPersonRepository.findByComparisonIdAndShortReference(comparison.id, comparisonPerson.shortReference),
    ).thenReturn(comparisonPerson)

    whenever(prisonService.getCurrentUserPrisonsList()).thenReturn(listOf("ABC"))
    whenever(bulkComparisonService.createDiscrepancy(any(), any(), any())).thenReturn(discrepancySummary)
    val discrepancyCause = DiscrepancyCause(DiscrepancyCategory.TUSED, DiscrepancySubCategory.REMAND_OR_UAL_RELATED)
    val discrepancyRequest = CreateComparisonDiscrepancyRequest(
      impact = discrepancyImpact.impact,
      listOf(discrepancyCause),
      detail = discrepancy.detail,
      priority = discrepancyPriority.priority,
      action = discrepancy.action,
    )
    val returnedSummary = comparisonService.createDiscrepancy(comparison.comparisonShortReference, comparisonPerson.shortReference, discrepancyRequest)

    verify(bulkComparisonService).createDiscrepancy(any(), any(), any())
    assertEquals(discrepancySummary, returnedSummary)
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
      calculatedByUsername = ManualComparisonServiceTest.USERNAME,
      comparisonStatus = ComparisonStatus(ComparisonStatusValue.COMPLETED),
      comparisonType = ComparisonType.ESTABLISHMENT_FULL,
      criteria = emptyObjectNode,
      prison = "all",
    )
    whenever(comparisonRepository.findByComparisonShortReference(any())).thenReturn(comparison)
    whenever(comparisonPersonRepository.findByComparisonIdAndShortReference(any(), any())).thenReturn(person)
    whenever(comparisonPersonDiscrepancyRepository.existsByComparisonPerson(any())).thenReturn(false)
    val result = comparisonService.getComparisonPersonByShortReference("ABCD1234", "FOO")
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
    id: Long,
    comparisonId: Long,
    calculationRequestId: Long,
    person: String,
    establishment: String? = null,
    hdcedFourPlusDate: LocalDate? = null,
  ): ComparisonPerson {
    val emptyObjectNode = objectMapper.createObjectNode()
    val emptyList: List<ValidationMessage> = emptyList()
    return ComparisonPerson(
      id,
      comparisonId,
      person = person,
      lastName = "Smith",
      latestBookingId = 25,
      isMatch = false,
      isValid = true,
      mismatchType = MismatchType.RELEASE_DATES_MISMATCH,
      validationMessages = objectMapper.valueToTree(emptyList),
      calculatedByUsername = USERNAME,
      nomisDates = emptyObjectNode,
      overrideDates = emptyObjectNode,
      breakdownByReleaseDateType = emptyObjectNode,
      calculationRequestId = calculationRequestId,
      sdsPlusSentencesIdentified = emptyObjectNode,
      establishment = establishment,
      hdcedFourPlusDate = hdcedFourPlusDate,
    )
  }

  companion object {
    const val USERNAME = "user1"
  }
}
