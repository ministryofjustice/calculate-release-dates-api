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
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.TestUtil
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
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offender
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceAndOffencesWithReleaseArrangements
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.Alert
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.OffenderOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonApiSentenceAndOffences
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType
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
import java.util.*

@ExtendWith(MockitoExtension::class)
class BulkComparisonServiceTest {
  private val comparisonPersonRepository = mock<ComparisonPersonRepository>()
  private val prisonService = mock<PrisonService>()
  private val calculationTransactionalService = mock<CalculationTransactionalService>()
  private val objectMapper: ObjectMapper = TestUtil.objectMapper()
  private val comparisonRepository = mock<ComparisonRepository>()
  private var pcscLookupService = mock<OffenceSdsPlusLookupService>()
  private val calculationReasonRepository = mock<CalculationReasonRepository>()
  private val comparisonPersonDiscrepancyRepository = mock<ComparisonPersonDiscrepancyRepository>()
  private val comparisonPersonDiscrepancyCategoryRepository = mock<ComparisonPersonDiscrepancyCategoryRepository>()
  private var serviceUserService = mock<ServiceUserService>()
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
  )

  private val releaseDates = someReleaseDates()

  private val calculatedReleaseDates = CalculatedReleaseDates(
    dates = releaseDates,
    calculationRequestId = 123,
    bookingId = 123,
    prisonerId = "ABC123DEF",
    calculationStatus = CalculationStatus.CONFIRMED,
    calculationReference = UUID.randomUUID(),
    calculationReason = CalculationReason(1, true, false, "Bulk Calculation", true, "UPDATE", nomisComment = "NOMIS_COMMENT", null),
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
    val comparison = Comparison(
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
      Booking(Offender("a", LocalDate.of(1980, 1, 1), true), emptyList(), Adjustments(), null, null, 123, true)
    val validationResult = ValidationResult(emptyList(), booking, duplicatedReleaseDates, null)
    whenever(calculationTransactionalService.validateAndCalculate(any(), any(), any(), any(), any(), any())).thenReturn(
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
      Booking(Offender("a", LocalDate.of(1980, 1, 1), true), emptyList(), Adjustments(), null, null, 123, true)
    val validationResult = ValidationResult(emptyList(), booking, null, null)
    whenever(calculationTransactionalService.validateAndCalculate(any(), any(), any(), any(), any(), any())).thenReturn(
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
      Booking(Offender("a", LocalDate.of(1980, 1, 1), true), emptyList(), Adjustments(), null, null, 123, true)
    val validationResult = ValidationResult(emptyList(), booking, null, null)
    whenever(calculationTransactionalService.validateAndCalculate(any(), any(), any(), any(), any(), any())).thenReturn(
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
      Booking(Offender("a", LocalDate.of(1980, 1, 1), true), emptyList(), Adjustments(), null, null, 123, true)
    val validationResult = ValidationResult(emptyList(), booking, calculatedReleaseDates, null)

    whenever(calculationTransactionalService.validateAndCalculate(any(), any(), any(), any(), any(), any())).thenReturn(
      validationResult,
    )

    val mismatch = bulkComparisonService.buildMismatch(calculableSentenceEnvelope, emptyList(), calculableSentenceEnvelope.sentenceAndOffences.map { SentenceAndOffencesWithReleaseArrangements(it, false) })

    assertTrue(mismatch.isValid)
    assertTrue(mismatch.isMatch)
    assertEquals(MismatchType.NONE, mismatch.type)
  }

  @Test
  fun `Determine mismatch invalid and not potential HDC4+ due being a sex offender`() {
    val booking =
      Booking(Offender("a", LocalDate.of(1980, 1, 1), true), emptyList(), Adjustments(), null, null, 123, true)
    val validationResult = ValidationResult(
      listOf(ValidationMessage(ValidationCode.SENTENCE_HAS_NO_IMPRISONMENT_TERM)),
      booking,
      calculatedReleaseDates,
      null,
    )

    whenever(calculationTransactionalService.validateAndCalculate(any(), any(), any(), any(), any(), any())).thenReturn(
      validationResult,
    )

    val mismatch = bulkComparisonService.buildMismatch(sexOffenderCalculableSentenceEnvelope, emptyList(), sexOffenderCalculableSentenceEnvelope.sentenceAndOffences.map { SentenceAndOffencesWithReleaseArrangements(it, false) })

    assertFalse(mismatch.isValid)
    assertFalse(mismatch.isMatch)
    assertEquals(MismatchType.VALIDATION_ERROR, mismatch.type)
  }

  @Test
  fun `Determine mismatch invalid and not potential HDC4+ due to sentence type`() {
    val booking =
      Booking(Offender("a", LocalDate.of(1980, 1, 1), true), emptyList(), Adjustments(), null, null, 123, true)
    val validationResult = ValidationResult(
      listOf(ValidationMessage(ValidationCode.SENTENCE_HAS_NO_IMPRISONMENT_TERM)),
      booking,
      calculatedReleaseDates,
      null,
    )

    whenever(calculationTransactionalService.validateAndCalculate(any(), any(), any(), any(), any(), any())).thenReturn(
      validationResult,
    )

    val notHdc4SentenceTypeEnvelope = calculableSentenceEnvelope.copy(
      sentenceAndOffences = listOf(sentenceAndOffence.copy(sentenceCalculationType = SentenceCalculationType.SEC236A.name)),
    )
    val mismatch = bulkComparisonService.buildMismatch(notHdc4SentenceTypeEnvelope, emptyList(), notHdc4SentenceTypeEnvelope.sentenceAndOffences.map { SentenceAndOffencesWithReleaseArrangements(it, false) })

    assertFalse(mismatch.isValid)
    assertFalse(mismatch.isMatch)
    assertEquals(MismatchType.VALIDATION_ERROR, mismatch.type)
  }

  @Test
  fun `Determine mismatch invalid and not potential HDC4+ due to sentence length under 4 years`() {
    val booking =
      Booking(Offender("a", LocalDate.of(1980, 1, 1), true), emptyList(), Adjustments(), null, null, 123, true)
    val validationResult = ValidationResult(
      listOf(ValidationMessage(ValidationCode.SENTENCE_HAS_NO_IMPRISONMENT_TERM)),
      booking,
      calculatedReleaseDates,
      null,
    )

    whenever(calculationTransactionalService.validateAndCalculate(any(), any(), any(), any(), any(), any())).thenReturn(
      validationResult,
    )

    val threeYearSentenceEnvelope = calculableSentenceEnvelope.copy(
      sentenceAndOffences = listOf(sentenceAndOffence.copy(terms = listOf(SentenceTerms(years = 3)))),
    )
    val mismatch = bulkComparisonService.buildMismatch(threeYearSentenceEnvelope, emptyList(), threeYearSentenceEnvelope.sentenceAndOffences.map { SentenceAndOffencesWithReleaseArrangements(it, false) })

    assertFalse(mismatch.isValid)
    assertFalse(mismatch.isMatch)
    assertEquals(MismatchType.VALIDATION_ERROR, mismatch.type)
  }

  @Test
  fun `Determine validation error mismatch not potential HDC4+ when there is a EDS sentence consecutive to an SDS sentence`() {
    val booking =
      Booking(Offender("a", LocalDate.of(1980, 1, 1), true), emptyList(), Adjustments(), null, null, 123, true)
    val validationResult = ValidationResult(
      listOf(ValidationMessage(ValidationCode.SENTENCE_HAS_NO_IMPRISONMENT_TERM)),
      booking,
      calculatedReleaseDates,
      null,
    )

    whenever(calculationTransactionalService.validateAndCalculate(any(), any(), any(), any(), any(), any())).thenReturn(
      validationResult,
    )

    val sdsSentence =
      sentenceAndOffence.copy(sentenceCalculationType = SentenceCalculationType.ADIMP.name, sentenceSequence = 1)
    val edsSentence = sentenceAndOffence.copy(
      sentenceCalculationType = SentenceCalculationType.EDS18.name,
      consecutiveToSequence = sdsSentence.sentenceSequence,
    )
    val sentencesAndOffences = listOf(sdsSentence, edsSentence)
    val consecutiveSentencesEnvelope = calculableSentenceEnvelope.copy(
      sentenceAndOffences = sentencesAndOffences,
    )

    val mismatch = bulkComparisonService.buildMismatch(consecutiveSentencesEnvelope, emptyList(), consecutiveSentencesEnvelope.sentenceAndOffences.map { SentenceAndOffencesWithReleaseArrangements(it, false) })

    assertFalse(mismatch.isValid)
    assertFalse(mismatch.isMatch)
    assertEquals(MismatchType.VALIDATION_ERROR, mismatch.type)
  }

  @Test
  fun `Determine validation error mismatch not potential HDC4+ when there is a SDS sentence consecutive to an SOPC sentence`() {
    val booking =
      Booking(Offender("a", LocalDate.of(1980, 1, 1), true), emptyList(), Adjustments(), null, null, 123, true)
    val validationResult = ValidationResult(
      listOf(ValidationMessage(ValidationCode.SENTENCE_HAS_NO_IMPRISONMENT_TERM)),
      booking,
      calculatedReleaseDates,
      null,
    )

    whenever(calculationTransactionalService.validateAndCalculate(any(), any(), any(), any(), any(), any())).thenReturn(
      validationResult,
    )

    val sdsSentence =
      sentenceAndOffence.copy(sentenceCalculationType = SentenceCalculationType.SOPC21.name, sentenceSequence = 56)
    val edsSentence = sentenceAndOffence.copy(
      sentenceCalculationType = SentenceCalculationType.SEC91_03_ORA.name,
      consecutiveToSequence = sdsSentence.sentenceSequence,
    )
    val sentencesAndOffences = listOf(sdsSentence, edsSentence)
    val consecutiveSentencesEnvelope = calculableSentenceEnvelope.copy(
      sentenceAndOffences = sentencesAndOffences,
    )

    val mismatch = bulkComparisonService.buildMismatch(consecutiveSentencesEnvelope, emptyList(), consecutiveSentencesEnvelope.sentenceAndOffences.map { SentenceAndOffencesWithReleaseArrangements(it, false) })

    assertFalse(mismatch.isValid)
    assertFalse(mismatch.isMatch)
    assertEquals(MismatchType.VALIDATION_ERROR, mismatch.type)
  }

  @Test
  fun `Determine potential HDC4+ mismatch`() {
    val booking =
      Booking(Offender("a", LocalDate.of(1980, 1, 1), true), emptyList(), Adjustments(), null, null, 123, true)
    val validationResult = ValidationResult(
      listOf(ValidationMessage(ValidationCode.SENTENCE_HAS_NO_IMPRISONMENT_TERM)),
      booking,
      calculatedReleaseDates,
      null,
    )

    whenever(calculationTransactionalService.validateAndCalculate(any(), any(), any(), any(), any(), any())).thenReturn(
      validationResult,
    )

    val mismatch = bulkComparisonService.buildMismatch(calculableSentenceEnvelope, emptyList(), calculableSentenceEnvelope.sentenceAndOffences.map { SentenceAndOffencesWithReleaseArrangements(it, false) })

    assertFalse(mismatch.isValid)
    assertFalse(mismatch.isMatch)
    assertEquals(MismatchType.VALIDATION_ERROR_HDC4_PLUS, mismatch.type)
  }

  @Test
  fun `Determine unsupported sentence type not HDC4+ because active sex offender`() {
    val booking =
      Booking(Offender("a", LocalDate.of(1980, 1, 1), true), emptyList(), Adjustments(), null, null, 123, true)
    val validationResult = ValidationResult(
      listOf(ValidationMessage(ValidationCode.UNSUPPORTED_SENTENCE_TYPE)),
      booking,
      calculatedReleaseDates,
      null,
    )

    whenever(calculationTransactionalService.validateAndCalculate(any(), any(), any(), any(), any(), any())).thenReturn(
      validationResult,
    )

    val mismatch = bulkComparisonService.buildMismatch(sexOffenderCalculableSentenceEnvelope, emptyList(), sexOffenderCalculableSentenceEnvelope.sentenceAndOffences.map { SentenceAndOffencesWithReleaseArrangements(it, false) })

    assertFalse(mismatch.isValid)
    assertFalse(mismatch.isMatch)
    assertEquals(MismatchType.UNSUPPORTED_SENTENCE_TYPE, mismatch.type)
  }

  @Test
  fun `Determine unsupported sentence type not HDC4+ because indeterminate sentence type`() {
    val booking =
      Booking(Offender("a", LocalDate.of(1980, 1, 1), true), emptyList(), Adjustments(), null, null, 123, true)
    val validationResult = ValidationResult(
      listOf(
        ValidationMessage(ValidationCode.FTR_28_DAYS_SENTENCE_LT_12_MONTHS),
        ValidationMessage(ValidationCode.UNSUPPORTED_SENTENCE_TYPE),
      ),
      booking,
      calculatedReleaseDates,
      null,
    )

    whenever(calculationTransactionalService.validateAndCalculate(any(), any(), any(), any(), any(), any())).thenReturn(
      validationResult,
    )

    val indeterminateSentenceEnvelope = calculableSentenceEnvelope.copy(
      sentenceAndOffences = listOf(sentenceAndOffence.copy(sentenceCalculationType = SentenceCalculationType.LIFE.name)),
    )
    val mismatch = bulkComparisonService.buildMismatch(indeterminateSentenceEnvelope, emptyList(), indeterminateSentenceEnvelope.sentenceAndOffences.map { SentenceAndOffencesWithReleaseArrangements(it, false) })

    assertFalse(mismatch.isValid)
    assertFalse(mismatch.isMatch)
    assertEquals(MismatchType.UNSUPPORTED_SENTENCE_TYPE, mismatch.type)
  }

  @Test
  fun `Determine unsupported sentence type not HDC4+ because duration less than 4 years`() {
    val booking =
      Booking(Offender("a", LocalDate.of(1980, 1, 1), true), emptyList(), Adjustments(), null, null, 123, true)
    val validationResult = ValidationResult(
      listOf(
        ValidationMessage(ValidationCode.UNSUPPORTED_SENTENCE_TYPE),
      ),
      booking,
      calculatedReleaseDates,
      null,
    )

    whenever(calculationTransactionalService.validateAndCalculate(any(), any(), any(), any(), any(), any())).thenReturn(
      validationResult,
    )
    val unsupportedSdsThreeYearsSentenceEnvelope = calculableSentenceEnvelope.copy(
      sentenceAndOffences = listOf(
        sentenceAndOffence.copy(
          sentenceCalculationType = SentenceCalculationType.LR_EPP.name,
          terms = listOf(
            SentenceTerms(years = 3),
          ),
        ),
      ),
    )

    val mismatch = bulkComparisonService.buildMismatch(unsupportedSdsThreeYearsSentenceEnvelope, emptyList(), unsupportedSdsThreeYearsSentenceEnvelope.sentenceAndOffences.map { SentenceAndOffencesWithReleaseArrangements(it, false) })

    assertFalse(mismatch.isValid)
    assertFalse(mismatch.isMatch)
    assertEquals(MismatchType.UNSUPPORTED_SENTENCE_TYPE, mismatch.type)
  }

  @Test
  fun `Determine unsupported sentence type not HDC4+ because of SDS+ offence`() {
    val booking =
      Booking(Offender("a", LocalDate.of(1980, 1, 1), true), emptyList(), Adjustments(), null, null, 123, true)
    val validationResult = ValidationResult(
      listOf(
        ValidationMessage(ValidationCode.UNSUPPORTED_SENTENCE_TYPE),
      ),
      booking,
      calculatedReleaseDates,
      null,
    )

    whenever(calculationTransactionalService.validateAndCalculate(any(), any(), any(), any(), any(), any())).thenReturn(
      validationResult,
    )
    whenever(calculationReasonRepository.findTopByIsBulkTrue()).thenReturn(Optional.of(bulkCalculationReason))

    val sdsPlusOffence = offenderOffence.copy(indicators = listOf(OffenderOffence.PCSC_SDS_PLUS))
    val sdsPlusSentence = sentenceAndOffence.copy(
      sentenceCalculationType = SentenceCalculationType.SEC91_03.name,
      offences = listOf(sdsPlusOffence),
    )

    val unsupportedSdsPlusSentenceEnvelope = calculableSentenceEnvelope.copy(
      sentenceAndOffences = listOf(
        sentenceAndOffence.copy(
          sentenceCalculationType = SentenceCalculationType.CIVIL.name,
        ),
        sdsPlusSentence,
      ),
    )

    val sdsPlusSentenceAndOffences = listOf(sdsPlusSentence)
    val mismatch = bulkComparisonService.buildMismatch(unsupportedSdsPlusSentenceEnvelope, sdsPlusSentenceAndOffences, unsupportedSdsPlusSentenceEnvelope.sentenceAndOffences.map { SentenceAndOffencesWithReleaseArrangements(it, false) })

    assertFalse(mismatch.isValid)
    assertFalse(mismatch.isMatch)
    assertEquals(MismatchType.UNSUPPORTED_SENTENCE_TYPE, mismatch.type)
  }

  @Test
  fun `Determine if a mismatch report is not valid due to unsupported sentence type and potential HDC4+`() {
    val booking =
      Booking(Offender("a", LocalDate.of(1980, 1, 1), true), emptyList(), Adjustments(), null, null, 123, true)
    val validationResult = ValidationResult(
      listOf(
        ValidationMessage(ValidationCode.FTR_28_DAYS_SENTENCE_LT_12_MONTHS),
        ValidationMessage(ValidationCode.UNSUPPORTED_SENTENCE_TYPE),
      ),
      booking,
      calculatedReleaseDates,
      null,
    )

    whenever(calculationTransactionalService.validateAndCalculate(any(), any(), any(), any(), any(), any())).thenReturn(
      validationResult,
    )

    whenever(calculationReasonRepository.findTopByIsBulkTrue()).thenReturn(Optional.of(bulkCalculationReason))

    val sentenceAndOffences = listOf(
      sentenceAndOffence.copy(sentenceCalculationType = SentenceCalculationType.LR_EPP.name),
      sentenceAndOffence.copy(sentenceCalculationType = SentenceCalculationType.ADIMP.name),
    )
    val unsupportedAndSdsFiveYearsSentenceEnvelope = calculableSentenceEnvelope.copy(
      sentenceAndOffences = sentenceAndOffences,
    )
    val mismatch = bulkComparisonService.buildMismatch(unsupportedAndSdsFiveYearsSentenceEnvelope, emptyList(), sentenceAndOffences.map { SentenceAndOffencesWithReleaseArrangements(it, false) })

    assertFalse(mismatch.isValid)
    assertFalse(mismatch.isMatch)
    assertEquals(MismatchType.UNSUPPORTED_SENTENCE_TYPE_FOR_HDC4_PLUS, mismatch.type)
  }

  @Test
  fun `should be unsupported sentence type for HDC4+ when unsupported sentence and consecutive SDS sentences of more than 4 years`() {
    val booking =
      Booking(Offender("a", LocalDate.of(1980, 1, 1), true), emptyList(), Adjustments(), null, null, 123, true)
    val validationResult = ValidationResult(
      listOf(
        ValidationMessage(ValidationCode.FTR_28_DAYS_SENTENCE_LT_12_MONTHS),
        ValidationMessage(ValidationCode.UNSUPPORTED_SENTENCE_TYPE),
      ),
      booking,
      calculatedReleaseDates,
      null,
    )

    whenever(calculationTransactionalService.validateAndCalculate(any(), any(), any(), any(), any(), any())).thenReturn(
      validationResult,
    )

    whenever(calculationReasonRepository.findTopByIsBulkTrue()).thenReturn(Optional.of(bulkCalculationReason))

    val sentence1 =
      sentenceAndOffence.copy(
        sentenceCalculationType = SentenceCalculationType.ADIMP.name,
        sentenceSequence = 1,
        terms = listOf(
          SentenceTerms(months = 4),
        ),
      )
    val sentence2 = sentenceAndOffence.copy(
      sentenceCalculationType = SentenceCalculationType.SEC91_03.name,
      sentenceSequence = 2,
      terms = listOf(
        SentenceTerms(years = 1),
      ),
    )
    val sentence3 =
      sentenceAndOffence.copy(
        sentenceCalculationType = SentenceCalculationType.ADIMP.name,
        sentenceSequence = 3,
        consecutiveToSequence = 1,
        terms = listOf(
          SentenceTerms(years = 3),
        ),
      )
    val sentence4 =
      sentenceAndOffence.copy(
        sentenceCalculationType = SentenceCalculationType.YOI_ORA.name,
        sentenceSequence = 4,
        consecutiveToSequence = 2,
        terms = listOf(
          SentenceTerms(months = 3),
        ),
      )
    val sentence5 =
      sentenceAndOffence.copy(
        sentenceCalculationType = SentenceCalculationType.SEC250.name,
        sentenceSequence = 5,
        terms = listOf(
          SentenceTerms(days = 5),
        ),
      )
    val sentence6 =
      sentenceAndOffence.copy(
        sentenceCalculationType = SentenceCalculationType.SEC91_03.name,
        sentenceSequence = 6,
        consecutiveToSequence = 3,
        terms = listOf(
          SentenceTerms(years = 1),
        ),
      )
    val sentence7 =
      sentenceAndOffence.copy(
        sentenceCalculationType = SentenceCalculationType.LR_EPP.name,
        sentenceSequence = 7,
        consecutiveToSequence = 6,
      )
    val sentencesAndOffences = listOf(sentence1, sentence2, sentence3, sentence4, sentence5, sentence6, sentence7)
    val consecutiveSentencesEnvelope = calculableSentenceEnvelope.copy(
      sentenceAndOffences = sentencesAndOffences,
    )

    val mismatch = bulkComparisonService.buildMismatch(consecutiveSentencesEnvelope, emptyList(), sentencesAndOffences.map { SentenceAndOffencesWithReleaseArrangements(it, false) })
    assertThat(mismatch.type).isEqualTo(MismatchType.UNSUPPORTED_SENTENCE_TYPE_FOR_HDC4_PLUS)
  }

  @Test
  fun `Determine if a mismatch is reported as an unsupported sentence type for an unsupported calculation validation`() {
    val booking =
      Booking(Offender("a", LocalDate.of(1980, 1, 1), true), emptyList(), Adjustments(), null, null, 123, true)
    val validationResult = ValidationResult(
      listOf(
        ValidationMessage(ValidationCode.UNSUPPORTED_CALCULATION_DTO_WITH_RECALL),
      ),
      booking,
      calculatedReleaseDates,
      null,
    )

    whenever(calculationTransactionalService.validateAndCalculate(any(), any(), any(), any(), any(), any())).thenReturn(
      validationResult,
    )

    whenever(calculationReasonRepository.findTopByIsBulkTrue()).thenReturn(Optional.of(bulkCalculationReason))

    val indeterminateSentenceEnvelope = calculableSentenceEnvelope.copy(
      sentenceAndOffences = listOf(sentenceAndOffence.copy(sentenceCalculationType = "LIFE")),
    )
    val mismatch = bulkComparisonService.buildMismatch(indeterminateSentenceEnvelope, emptyList(), indeterminateSentenceEnvelope.sentenceAndOffences.map { SentenceAndOffencesWithReleaseArrangements(it, false) })

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
      Booking(Offender("a", LocalDate.of(1980, 1, 1), true), emptyList(), Adjustments(), null, null, 123, true)
    val validationResult = ValidationResult(emptyList(), booking, duplicatedReleaseDates, null)

    whenever(calculationTransactionalService.validateAndCalculate(any(), any(), any(), any(), any(), any())).thenReturn(
      validationResult,
    )

    whenever(calculationReasonRepository.findTopByIsBulkTrue()).thenReturn(Optional.of(bulkCalculationReason))

    val mismatch = bulkComparisonService.buildMismatch(calculableSentenceEnvelope, emptyList(), calculableSentenceEnvelope.sentenceAndOffences.map { SentenceAndOffencesWithReleaseArrangements(it, false) })

    assertTrue(mismatch.isValid)
    assertFalse(mismatch.isMatch)
    assertEquals(MismatchType.RELEASE_DATES_MISMATCH, mismatch.type)
  }

  @Test
  fun `Should set HDCED4PLUS date if not the same as HDCED`() {
    val comparison = Comparison(
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
    val duplicateReleaseDates = releaseDates.toMutableMap()
    duplicateReleaseDates[ReleaseDateType.HDCED4PLUS] = LocalDate.of(2022, 1, 1)

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
      Booking(Offender("a", LocalDate.of(1980, 1, 1), true), emptyList(), Adjustments(), null, null, 123, true)
    val validationResult = ValidationResult(emptyList(), booking, duplicatedReleaseDates, null)
    whenever(calculationTransactionalService.validateAndCalculate(any(), any(), any(), any(), any(), any())).thenReturn(
      validationResult,
    )

    whenever(calculationReasonRepository.findTopByIsBulkTrue()).thenReturn(Optional.of(bulkCalculationReason))

    whenever(prisonService.getActiveBookingsByEstablishment(comparison.prison!!, "")).thenReturn(
      listOf(
        sexOffenderCalculableSentenceEnvelope,
      ),
    )

    bulkComparisonService.processPrisonComparison(comparison, "")

    val comparisonPersonCaptor = ArgumentCaptor.forClass(ComparisonPerson::class.java)
    verify(comparisonPersonRepository).save(comparisonPersonCaptor.capture())

    val comparisonPerson = comparisonPersonCaptor.value
    assertThat(comparisonPerson.hdcedFourPlusDate).isEqualTo(LocalDate.of(2022, 1, 1))
  }

  @Test
  fun `Should record HDCED4PLUS date mismatch for ESTABLISHMENT_HDCED4PLUS comparison type`() {
    val comparison = Comparison(
      1,
      UUID.randomUUID(),
      "ABCD1234",
      objectMapper.createObjectNode(),
      "BMI",
      ComparisonType.ESTABLISHMENT_HDCED4PLUS,
      LocalDateTime.now(),
      "SOMEONE",
      ComparisonStatus(ComparisonStatusValue.PROCESSING),
    )
    val duplicateReleaseDates = releaseDates.toMutableMap()
    duplicateReleaseDates[ReleaseDateType.HDCED4PLUS] = LocalDate.of(2022, 1, 1)

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
      Booking(Offender("a", LocalDate.of(1980, 1, 1), true), emptyList(), Adjustments(), null, null, 123, true)
    val validationResult = ValidationResult(emptyList(), booking, duplicatedReleaseDates, null)
    whenever(calculationTransactionalService.validateAndCalculate(any(), any(), any(), any(), any(), any())).thenReturn(
      validationResult,
    )
    whenever(calculationReasonRepository.findTopByIsBulkTrue()).thenReturn(Optional.of(bulkCalculationReason))
    whenever(prisonService.getActiveBookingsByEstablishment(comparison.prison!!, "")).thenReturn(
      listOf(
        sexOffenderCalculableSentenceEnvelope,
      ),
    )

    bulkComparisonService.processPrisonComparison(comparison, "")

    val comparisonPersonCaptor = ArgumentCaptor.forClass(ComparisonPerson::class.java)
    verify(comparisonPersonRepository).save(comparisonPersonCaptor.capture())

    val comparisonPerson = comparisonPersonCaptor.value
    assertThat(comparisonPerson.hdcedFourPlusDate).isEqualTo(LocalDate.of(2022, 1, 1))
  }

  @Test
  fun `Should set HDCED4PLUS date to null if same`() {
    val comparison = Comparison(
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
    val duplicateReleaseDates = releaseDates.toMutableMap()
    duplicateReleaseDates[ReleaseDateType.HDCED4PLUS] = LocalDate.of(2026, 1, 1)

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
      Booking(Offender("a", LocalDate.of(1980, 1, 1), true), emptyList(), Adjustments(), null, null, 123, true)
    val validationResult = ValidationResult(emptyList(), booking, duplicatedReleaseDates, null)
    whenever(calculationTransactionalService.validateAndCalculate(any(), any(), any(), any(), any(), any())).thenReturn(
      validationResult,
    )

    whenever(calculationReasonRepository.findTopByIsBulkTrue()).thenReturn(Optional.of(bulkCalculationReason))

    whenever(prisonService.getActiveBookingsByEstablishment(comparison.prison!!, "")).thenReturn(
      listOf(
        sexOffenderCalculableSentenceEnvelope,
      ),
    )

    bulkComparisonService.processPrisonComparison(comparison, "")

    val comparisonPersonCaptor = ArgumentCaptor.forClass(ComparisonPerson::class.java)
    verify(comparisonPersonRepository).save(comparisonPersonCaptor.capture())

    val comparisonPerson = comparisonPersonCaptor.value
    assertThat(comparisonPerson.hdcedFourPlusDate).isNull()
  }

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

  private val bulkCalculationReason = CalculationReason(1, true, false, "Bulk Calculation", true, "UPDATE", nomisComment = "NOMIS_COMMENT", null)

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
