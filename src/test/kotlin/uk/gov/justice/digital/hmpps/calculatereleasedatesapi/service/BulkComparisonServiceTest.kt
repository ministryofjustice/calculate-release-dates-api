package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.firstValue
import org.mockito.kotlin.lastValue
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.retry.support.RetryTemplate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.TestUtil
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.FeatureToggles
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationReason
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.Comparison
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.ComparisonPerson
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.ComparisonPersonDiscrepancy
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.ComparisonPersonDiscrepancyImpact
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.ComparisonPersonDiscrepancyPriority
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.ComparisonStatus
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationStatus
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ComparisonStatusValue
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ComparisonType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.DiscrepancyCategory
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.DiscrepancyImpact
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.DiscrepancyPriority
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.DiscrepancySubCategory
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Adjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculatedReleaseDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CreateComparisonDiscrepancyRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.DiscrepancyCause
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.MismatchType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.NormalisedSentenceAndOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offender
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SDSEarlyReleaseExclusionType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceAndOffenceWithReleaseArrangements
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.Alert
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.OffenderOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonApiSentenceAndOffences
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceTerms
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi.CalculableSentenceEnvelope
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi.Person
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationReasonRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.ComparisonPersonDiscrepancyCategoryRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.ComparisonPersonDiscrepancyRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.ComparisonPersonRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.ComparisonRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationMessage
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationResult
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class BulkComparisonServiceTest {
  private val comparisonPersonRepository = mock<ComparisonPersonRepository>()
  private val prisonService = mock<PrisonService>()
  private val calculationTransactionalService = mock<CalculationTransactionalService>()
  private val objectMapper: ObjectMapper = TestUtil.objectMapper()
  private val comparisonRepository = mock<ComparisonRepository>()
  private var pcscLookupService = mock<ReleaseArrangementLookupService>()
  private val calculationReasonRepository = mock<CalculationReasonRepository>()
  private val comparisonPersonDiscrepancyRepository = mock<ComparisonPersonDiscrepancyRepository>()
  private val comparisonPersonDiscrepancyCategoryRepository = mock<ComparisonPersonDiscrepancyCategoryRepository>()
  private var serviceUserService = mock<ServiceUserService>()
  private val botusTusedService = mock<BotusTusedService>()
  private val retryTemplate = RetryTemplate.builder().maxAttempts(3).build() // No backoff to keep test fast
  private val bulkComparisonService: BulkComparisonService = BulkComparisonService(
    comparisonPersonRepository,
    prisonService,
    calculationTransactionalService,
    objectMapper,
    comparisonRepository,
    pcscLookupService,
    calculationReasonRepository,
    comparisonPersonDiscrepancyRepository,
    comparisonPersonDiscrepancyCategoryRepository,
    serviceUserService,
    botusTusedService,
    FeatureToggles(),
    retryTemplate,
  )

  private val releaseDates = someReleaseDates()

  private val calculatedReleaseDates = CalculatedReleaseDates(
    dates = releaseDates,
    calculationRequestId = 123,
    bookingId = 123,
    prisonerId = "ABC123DEF",
    calculationStatus = CalculationStatus.CONFIRMED,
    calculationReference = UUID.randomUUID(),
    calculationReason = CalculationReason(
      1,
      true,
      false,
      "Bulk Calculation",
      true,
      "UPDATE",
      nomisComment = "NOMIS_COMMENT",
      null,
    ),
    calculationDate = LocalDate.of(2024, 1, 1),
  )

  private val offenderOffence = OffenderOffence(
    123,
    LocalDate.of(2012, 1, 1),
    LocalDate.of(2012, 1, 1),
    "AB123DEF",
    "finagling",
    emptyList(),
  )

  private val sentenceAndOffence = PrisonApiSentenceAndOffences(
    bookingId = 12345,
    sentenceSequence = 0,
    lineSequence = 2,
    caseSequence = 1,
    sentenceDate = LocalDate.of(2012, 1, 1),
    terms = listOf(
      SentenceTerms(years = 5),
    ),
    sentenceStatus = "A",
    sentenceCategory = "SEN",
    sentenceCalculationType = "SEC91_03",
    sentenceTypeDescription = "DESC",
    offences = listOf(offenderOffence),
  )

  private val calculableSentenceEnvelope = CalculableSentenceEnvelope(
    person = Person("A", LocalDate.of(1990, 5, 1), "Morris", "HDR", emptyList()),
    bookingId = 12345,
    sentenceAndOffences = listOf(sentenceAndOffence),
    sentenceAdjustments = emptyList(),
    bookingAdjustments = emptyList(),
    offenderFinePayments = emptyList(),
    fixedTermRecallDetails = null,
    sentenceCalcDates = calculatedReleaseDates.toSentenceCalcDates(),
  )

  private val sexOffenderCalculableSentenceEnvelope = calculableSentenceEnvelope.copy(
    person = Person(
      "A",
      LocalDate.of(1990, 5, 1),
      "Rickman",
      "KQR",
      alerts = listOf(Alert(LocalDate.now(), alertType = "S", alertCode = "SR")),
    ),
  )

  @BeforeEach
  fun beforeEach() {
    whenever(calculationReasonRepository.findTopByIsBulkTrue()).thenReturn(Optional.of(bulkCalculationReason))
  }

  @Test
  fun `Should create a prison comparison`() {
    val comparison = aBasicComparison()
    val duplicateReleaseDates = releaseDates.toMutableMap()
    duplicateReleaseDates[ReleaseDateType.SED] = LocalDate.of(2022, 1, 1)

    val duplicatedReleaseDates = CalculatedReleaseDates(
      dates = duplicateReleaseDates,
      calculationRequestId = 123,
      bookingId = 123,
      prisonerId = "ABC123DEF",
      calculationStatus = CalculationStatus.CONFIRMED,
      calculationReference = UUID.randomUUID(),
      calculationReason = bulkCalculationReason,
      calculationDate = LocalDate.of(2024, 1, 1),
    )

    val booking =
      Booking(Offender("a", LocalDate.of(1980, 1, 1), true), emptyList(), Adjustments(), null, null, 123)
    val validationResult = ValidationResult(emptyList(), booking, duplicatedReleaseDates, null)
    whenever(calculationTransactionalService.validateAndCalculate(any(), any(), any(), any(), any())).thenReturn(
      validationResult,
    )

    whenever(prisonService.getActiveBookingsByEstablishment(comparison.prison!!, "")).thenReturn(
      listOf(
        sexOffenderCalculableSentenceEnvelope,
      ),
    )

    bulkComparisonService.processPrisonComparison(comparison, "")

    val comparisonStatus = comparison.comparisonStatus
    assertThat(comparisonStatus.name).isEqualTo(ComparisonStatusValue.COMPLETED.name)
    assertThat(comparison.numberOfPeopleCompared).isEqualTo(1)
    val comparisonPersonCaptor = ArgumentCaptor.forClass(ComparisonPerson::class.java)
    verify(comparisonPersonRepository).save(comparisonPersonCaptor.capture())

    val comparisonPerson = comparisonPersonCaptor.value
    assertThat(comparisonPerson.person).isEqualTo(calculableSentenceEnvelope.person.prisonerNumber)
    assertThat(comparisonPerson.latestBookingId).isEqualTo(calculableSentenceEnvelope.bookingId)
    assertThat(comparisonPerson.isMatch).isEqualTo(false)
    assertThat(comparisonPerson.isValid).isEqualTo(true)
    assertThat(comparisonPerson.calculatedByUsername).isEqualTo(comparison.calculatedByUsername)
    assertThat(comparisonPerson.isActiveSexOffender).isEqualTo(true)
  }

  @Test
  fun `Should create a prison comparison with multiple envelopes efficiently`() {
    val comparison = aBasicComparison()
    val duplicatedReleaseDates = sameReleaseDates()

    val booking =
      Booking(Offender("a", LocalDate.of(1980, 1, 1), true), emptyList(), Adjustments(), null, null, 123)
    val validationResult = ValidationResult(emptyList(), booking, duplicatedReleaseDates, null)
    whenever(calculationTransactionalService.validateAndCalculate(any(), any(), any(), any(), any())).thenReturn(
      validationResult,
    )

    whenever(prisonService.getActiveBookingsByEstablishment(comparison.prison!!, "")).thenReturn(
      listOf(
        calculableSentenceEnvelope,
        sexOffenderCalculableSentenceEnvelope,
      ),
    )

    bulkComparisonService.processPrisonComparison(comparison, "")

    val comparisonStatus = comparison.comparisonStatus
    assertThat(comparisonStatus.name).isEqualTo(ComparisonStatusValue.COMPLETED.name)
    assertThat(comparison.numberOfPeopleCompared).isEqualTo(2)
    verify(pcscLookupService, times(2)).populateReleaseArrangements(any())
  }

  @Test
  fun `Should retry failures`() {
    val comparison = aBasicComparison()

    val duplicatedReleaseDates = sameReleaseDates()

    val booking =
      Booking(Offender("a", LocalDate.of(1980, 1, 1), true), emptyList(), Adjustments(), null, null, 123)
    val validationResult = ValidationResult(emptyList(), booking, duplicatedReleaseDates, null)
    whenever(calculationTransactionalService.validateAndCalculate(any(), any(), any(), any(), any())).thenReturn(
      validationResult,
    )

    whenever(prisonService.getActiveBookingsByEstablishment(comparison.prison!!, "")).thenReturn(
      listOf(calculableSentenceEnvelope),
    )

    whenever(pcscLookupService.populateReleaseArrangements(any()))
      .thenThrow(RuntimeException("Bang!"))
      .thenThrow(RuntimeException("Bang!"))
      .thenReturn(listOf())

    bulkComparisonService.processPrisonComparison(comparison, "")

    val comparisonStatus = comparison.comparisonStatus
    assertThat(comparisonStatus.name).isEqualTo(ComparisonStatusValue.COMPLETED.name)
    assertThat(comparison.numberOfPeopleCompared).isEqualTo(1)
    verify(pcscLookupService, times(3)).populateReleaseArrangements(any())
  }

  @Test
  fun `If a single envelope fails even after retry then continue with the rest`() {
    val comparison = aBasicComparison()

    val duplicatedReleaseDates = sameReleaseDates()

    val booking =
      Booking(Offender("a", LocalDate.of(1980, 1, 1), true), emptyList(), Adjustments(), null, null, 123)
    val validationResult = ValidationResult(emptyList(), booking, duplicatedReleaseDates, null)
    whenever(calculationTransactionalService.validateAndCalculate(any(), any(), any(), any(), any())).thenReturn(
      validationResult,
    )

    whenever(prisonService.getActiveBookingsByEstablishment(comparison.prison!!, "")).thenReturn(
      listOf(
        calculableSentenceEnvelope,
        sexOffenderCalculableSentenceEnvelope,
      ),
    )

    whenever(pcscLookupService.populateReleaseArrangements(any()))
      .thenThrow(RuntimeException("Bang!"))
      .thenThrow(RuntimeException("Bang!"))
      .thenThrow(RuntimeException("Bang!"))
      // 1st envelope fails after 3 attempts and 2nd will always pass
      .thenReturn(listOf())

    bulkComparisonService.processPrisonComparison(comparison, "")

    val comparisonStatus = comparison.comparisonStatus
    assertThat(comparisonStatus.name).isEqualTo(ComparisonStatusValue.COMPLETED.name)
    assertThat(comparison.numberOfPeopleCompared).isEqualTo(2)
    assertThat(comparison.numberOfPeopleComparisonFailedFor).isEqualTo(1)
    val argumentCaptor = ArgumentCaptor.forClass(ComparisonPerson::class.java)
    verify(comparisonPersonRepository, times(2)).save(argumentCaptor.capture())
    val failedComparisonPerson = argumentCaptor.firstValue
    assertThat(failedComparisonPerson.mismatchType).isEqualTo(MismatchType.FATAL_EXCEPTION)
    assertThat(failedComparisonPerson.fatalException).isEqualTo("Bang!")
    assertThat(failedComparisonPerson.isFatal).isTrue()

    val successComparisonPerson = argumentCaptor.lastValue
    assertThat(successComparisonPerson.mismatchType).isEqualTo(MismatchType.NONE)
    assertThat(successComparisonPerson.fatalException).isNull()
    assertThat(successComparisonPerson.isFatal).isFalse()
  }

  @Test
  fun `should trim fatal exceptions to 256 chars`() {
    val comparison = aBasicComparison()

    val duplicatedReleaseDates = sameReleaseDates()

    val booking =
      Booking(Offender("a", LocalDate.of(1980, 1, 1), true), emptyList(), Adjustments(), null, null, 123)
    val validationResult = ValidationResult(emptyList(), booking, duplicatedReleaseDates, null)
    whenever(calculationTransactionalService.validateAndCalculate(any(), any(), any(), any(), any())).thenReturn(
      validationResult,
    )

    whenever(prisonService.getActiveBookingsByEstablishment(comparison.prison!!, "")).thenReturn(
      listOf(
        calculableSentenceEnvelope,
      ),
    )

    val aReallyLongException = List(25) { "ABCDEFGHIJKLMNOPQRSTUVWXYZ" }.joinToString()
    assertThat(aReallyLongException).hasSizeGreaterThan(256)
    whenever(pcscLookupService.populateReleaseArrangements(any()))
      .thenThrow(RuntimeException(aReallyLongException))
      .thenThrow(RuntimeException(aReallyLongException))
      .thenThrow(RuntimeException(aReallyLongException))

    bulkComparisonService.processPrisonComparison(comparison, "")

    val argumentCaptor = ArgumentCaptor.forClass(ComparisonPerson::class.java)
    verify(comparisonPersonRepository).save(argumentCaptor.capture())
    val failedComparisonPerson = argumentCaptor.firstValue
    assertThat(failedComparisonPerson.mismatchType).isEqualTo(MismatchType.FATAL_EXCEPTION)
    assertThat(failedComparisonPerson.fatalException).hasSize(256)
  }

  private fun sameReleaseDates() = CalculatedReleaseDates(
    dates = releaseDates,
    calculationRequestId = 123,
    bookingId = 123,
    prisonerId = "ABC123DEF",
    calculationStatus = CalculationStatus.CONFIRMED,
    calculationReference = UUID.randomUUID(),
    calculationReason = bulkCalculationReason,
    calculationDate = LocalDate.of(2024, 1, 1),
  )

  @Test
  fun `Should create an all prisons comparison`() {
    val comparison = Comparison(
      1,
      UUID.randomUUID(),
      "ABCD1234",
      objectMapper.createObjectNode(),
      "ALL",
      ComparisonType.ESTABLISHMENT_FULL,
      LocalDateTime.now(),
      "SOMEONE",
      ComparisonStatus(ComparisonStatusValue.PROCESSING),
    )

    whenever(prisonService.getCurrentUserPrisonsList()).thenReturn(listOf("ABC", "DEF"))
    whenever(prisonService.getActiveBookingsByEstablishment("ABC", "")).thenReturn(
      listOf(
        calculableSentenceEnvelope,
      ),
    )
    whenever(prisonService.getActiveBookingsByEstablishment("DEF", "")).thenReturn(
      listOf(
        calculableSentenceEnvelope,
      ),
    )

    val booking =
      Booking(Offender("a", LocalDate.of(1980, 1, 1), true), emptyList(), Adjustments(), null, null, 123)
    val validationResult = ValidationResult(emptyList(), booking, null, null)
    whenever(calculationTransactionalService.validateAndCalculate(any(), any(), any(), any(), any())).thenReturn(
      validationResult,
    )

    bulkComparisonService.processFullCaseLoadComparison(comparison, "")

    val comparisonStatus = comparison.comparisonStatus
    assertThat(comparisonStatus.name).isEqualTo(ComparisonStatusValue.COMPLETED.name)
    assertThat(comparison.numberOfPeopleCompared).isEqualTo(2)
  }

  @Test
  fun `Should create a manual comparison`() {
    val comparison = Comparison(
      1,
      UUID.randomUUID(),
      "ABCD1234",
      objectMapper.createObjectNode(),
      "BNI",
      ComparisonType.MANUAL,
      LocalDateTime.now(),
      "SOMEONE",
      ComparisonStatus(ComparisonStatusValue.PROCESSING),
    )

    val prisonerIds = listOf("A7542DZ")
    val token = "a-token"
    whenever(prisonService.getActiveBookingsByPrisonerIds(prisonerIds, token)).thenReturn(
      listOf(
        calculableSentenceEnvelope,
      ),
    )
    val booking =
      Booking(Offender("a", LocalDate.of(1980, 1, 1), true), emptyList(), Adjustments(), null, null, 123)
    val validationResult = ValidationResult(emptyList(), booking, null, null)
    whenever(calculationTransactionalService.validateAndCalculate(any(), any(), any(), any(), any())).thenReturn(
      validationResult,
    )

    bulkComparisonService.processManualComparison(comparison, prisonerIds, token)

    val comparisonStatus = comparison.comparisonStatus
    assertThat(comparisonStatus.name).isEqualTo(ComparisonStatusValue.COMPLETED.name)
    assertThat(comparison.numberOfPeopleCompared).isEqualTo(1)
    val comparisonPersonCaptor = ArgumentCaptor.forClass(ComparisonPerson::class.java)
    verify(comparisonPersonRepository).save(comparisonPersonCaptor.capture())

    val comparisonPerson = comparisonPersonCaptor.value
    assertThat(comparisonPerson.person).isEqualTo(calculableSentenceEnvelope.person.prisonerNumber)
    assertThat(comparisonPerson.latestBookingId).isEqualTo(calculableSentenceEnvelope.bookingId)
    assertThat(comparisonPerson.isMatch).isEqualTo(true)
    assertThat(comparisonPerson.isValid).isEqualTo(true)
    assertThat(comparisonPerson.calculatedByUsername).isEqualTo(comparison.calculatedByUsername)
    assertThat(comparisonPerson.isActiveSexOffender).isEqualTo(false)
  }

  @Test
  fun `Determine if a mismatch report is valid and is a match`() {
    val booking =
      Booking(Offender("a", LocalDate.of(1980, 1, 1), true), emptyList(), Adjustments(), null, null, 123)
    val validationResult = ValidationResult(emptyList(), booking, calculatedReleaseDates, null)

    whenever(calculationTransactionalService.validateAndCalculate(any(), any(), any(), any(), any())).thenReturn(
      validationResult,
    )

    val mismatch = bulkComparisonService.buildMismatch(
      calculableSentenceEnvelope,
      calculableSentenceEnvelope.sentenceAndOffences.flatMap { sentence ->
        sentence.offences.map {
          NormalisedSentenceAndOffence(
            sentence,
            it,
          )
        }
      }.map {
        SentenceAndOffenceWithReleaseArrangements(
          it,
          isSdsPlus = false,
          isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
          isSDSPlusOffenceInPeriod = false,
          hasAnSDSExclusion = SDSEarlyReleaseExclusionType.NO,
        )
      },
    )

    assertTrue(mismatch.isValid)
    assertTrue(mismatch.isMatch)
    assertEquals(MismatchType.NONE, mismatch.type)
  }

  @Test
  fun `Determine if a mismatch is reported as an unsupported sentence type for an unsupported calculation validation`() {
    val booking =
      Booking(Offender("a", LocalDate.of(1980, 1, 1), true), emptyList(), Adjustments(), null, null, 123)
    val validationResult = ValidationResult(
      listOf(
        ValidationMessage(ValidationCode.UNSUPPORTED_CALCULATION_DTO_WITH_RECALL),
      ),
      booking,
      calculatedReleaseDates,
      null,
    )

    whenever(calculationTransactionalService.validateAndCalculate(any(), any(), any(), any(), any())).thenReturn(
      validationResult,
    )

    whenever(calculationReasonRepository.findTopByIsBulkTrue()).thenReturn(Optional.of(bulkCalculationReason))

    val indeterminateSentenceEnvelope = calculableSentenceEnvelope.copy(
      sentenceAndOffences = listOf(sentenceAndOffence.copy(sentenceCalculationType = "LIFE")),
    )
    val mismatch = bulkComparisonService.buildMismatch(
      indeterminateSentenceEnvelope,
      indeterminateSentenceEnvelope.sentenceAndOffences.flatMap { sentence ->
        sentence.offences.map {
          NormalisedSentenceAndOffence(
            sentence,
            it,
          )
        }
      }.map {
        SentenceAndOffenceWithReleaseArrangements(
          it,
          isSdsPlus = false,
          isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
          isSDSPlusOffenceInPeriod = false,
          hasAnSDSExclusion = SDSEarlyReleaseExclusionType.NO,
        )
      },
    )

    assertFalse(mismatch.isValid)
    assertFalse(mismatch.isMatch)
    assertEquals(MismatchType.UNSUPPORTED_SENTENCE_TYPE, mismatch.type)
  }

  @Test
  fun `Determine if a mismatch report isValid and not isMatch due to release dates mismatch`() {
    val duplicateReleaseDates = releaseDates.toMutableMap()
    duplicateReleaseDates[ReleaseDateType.SED] = LocalDate.of(2022, 1, 1)

    val duplicatedReleaseDates = CalculatedReleaseDates(
      dates = duplicateReleaseDates,
      calculationRequestId = 123,
      bookingId = 123,
      prisonerId = "ABC123DEF",
      calculationStatus = CalculationStatus.CONFIRMED,
      calculationReference = UUID.randomUUID(),
      calculationReason = bulkCalculationReason,
      calculationDate = LocalDate.of(2024, 1, 1),
    )

    val booking =
      Booking(Offender("a", LocalDate.of(1980, 1, 1), true), emptyList(), Adjustments(), null, null, 123)
    val validationResult = ValidationResult(emptyList(), booking, duplicatedReleaseDates, null)

    whenever(calculationTransactionalService.validateAndCalculate(any(), any(), any(), any(), any())).thenReturn(
      validationResult,
    )

    whenever(calculationReasonRepository.findTopByIsBulkTrue()).thenReturn(Optional.of(bulkCalculationReason))

    val mismatch = bulkComparisonService.buildMismatch(
      calculableSentenceEnvelope,
      calculableSentenceEnvelope.sentenceAndOffences.flatMap { sentence ->
        sentence.offences.map {
          NormalisedSentenceAndOffence(
            sentence,
            it,
          )
        }
      }.map {
        SentenceAndOffenceWithReleaseArrangements(
          it,
          isSdsPlus = false,
          isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
          isSDSPlusOffenceInPeriod = false,
          hasAnSDSExclusion = SDSEarlyReleaseExclusionType.NO,
        )
      },
    )

    assertTrue(mismatch.isValid)
    assertFalse(mismatch.isMatch)
    assertEquals(MismatchType.RELEASE_DATES_MISMATCH, mismatch.type)
  }

  private fun aBasicComparison() = Comparison(
    1,
    UUID.randomUUID(),
    "ABCD1234",
    objectMapper.createObjectNode(),
    "BMI",
    ComparisonType.ESTABLISHMENT_FULL,
    LocalDateTime.now(),
    "SOMEONE",
    ComparisonStatus(ComparisonStatusValue.PROCESSING),
  )

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
    whenever(
      comparisonRepository.findByComparisonShortReference("ABCD1234"),
    ).thenReturn(comparison)
    whenever(
      comparisonPersonRepository.findByComparisonIdAndShortReference(
        comparison.id,
        comparisonPerson.shortReference,
      ),
    ).thenReturn(comparisonPerson)
    whenever(comparisonPersonDiscrepancyRepository.save(any())).thenReturn(discrepancy)
    val discrepancyCause = DiscrepancyCause(DiscrepancyCategory.TUSED, DiscrepancySubCategory.REMAND_OR_UAL_RELATED)
    val discrepancyRequest = CreateComparisonDiscrepancyRequest(
      impact = discrepancyImpact.impact,
      listOf(discrepancyCause),
      detail = discrepancy.detail,
      priority = discrepancyPriority.priority,
      action = discrepancy.action,
    )
    val discrepancySummary = bulkComparisonService.createDiscrepancy(
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
    whenever(
      comparisonRepository.findByComparisonShortReference("ABCD1234"),
    ).thenReturn(comparison)
    whenever(
      comparisonPersonRepository.findByComparisonIdAndShortReference(
        comparison.id,
        comparisonPerson.shortReference,
      ),
    ).thenReturn(comparisonPerson)
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
    val discrepancySummary = bulkComparisonService.createDiscrepancy(
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

  private val bulkCalculationReason =
    CalculationReason(1, true, false, "Bulk Calculation", true, "UPDATE", nomisComment = "NOMIS_COMMENT", null)

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
