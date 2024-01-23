package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.TestUtil
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationReason
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.Comparison
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.ComparisonPerson
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.ComparisonStatus
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationStatus
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ComparisonStatusValue
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ComparisonType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Adjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculatedReleaseDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.MismatchType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offender
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.Alert
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.OffenderOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAndOffences
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceTerms
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi.CalculableSentenceEnvelope
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi.Person
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationReasonRepository
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
  private var pcscLookupService = mock<OffenceSdsPlusLookupService>()
  private val calculationReasonRepository = mock<CalculationReasonRepository>()
  private val bulkComparisonService: BulkComparisonService = BulkComparisonService(
    comparisonPersonRepository,
    prisonService,
    calculationTransactionalService,
    objectMapper,
    comparisonRepository,
    pcscLookupService,
    calculationReasonRepository,
  )

  private val releaseDates = someReleaseDates()

  private val calculatedReleaseDates = CalculatedReleaseDates(
    dates = releaseDates,
    calculationRequestId = 123,
    bookingId = 123,
    prisonerId = "ABC123DEF",
    calculationStatus = CalculationStatus.CONFIRMED,
    calculationReference = UUID.randomUUID(),
    calculationReason = CalculationReason(1, true, false, "Bulk Calculation", true),
  )

  private val offenderOffence = OffenderOffence(
    123,
    LocalDate.of(2012, 1, 1),
    LocalDate.of(2012, 1, 1),
    "AB123DEF",
    "finagling",
    emptyList(),
  )

  private val sentenceAndOffence = SentenceAndOffences(
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
    person = Person("A", LocalDate.of(1990, 5, 1), "Morris", emptyList()),
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
      alerts = listOf(Alert(LocalDate.now(), alertType = "S", alertCode = "SR")),
    ),
  )

  @Test
  fun `Should create a prison comparison`() {
    val comparison = Comparison(
      1, UUID.randomUUID(), "ABCD1234", objectMapper.createObjectNode(), "BMI", ComparisonType.ESTABLISHMENT_FULL, LocalDateTime.now(), "SOMEONE",
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
      calculationReason = BULK_CALCULATION_REASON,
    )

    val booking =
      Booking(Offender("a", LocalDate.of(1980, 1, 1), true), emptyList(), Adjustments(), null, null, 123, true)
    val validationResult = ValidationResult(emptyList(), booking, duplicatedReleaseDates, null)
    whenever(calculationTransactionalService.validateAndCalculate(any(), any(), any(), any(), any(), any())).thenReturn(
      validationResult,
    )

    whenever(calculationReasonRepository.findTopByIsBulkTrue()).thenReturn(Optional.of(BULK_CALCULATION_REASON))

    whenever(prisonService.getActiveBookingsByEstablishment(comparison.prison!!, "")).thenReturn(
      listOf(
        sexOffenderCalculableSentenceEnvelope,
      ),
    )

    bulkComparisonService.processPrisonComparison(comparison, "")

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
  fun `Determine if a mismatch report is valid and is a match`() {
    val booking =
      Booking(Offender("a", LocalDate.of(1980, 1, 1), true), emptyList(), Adjustments(), null, null, 123, true)
    val validationResult = ValidationResult(emptyList(), booking, calculatedReleaseDates, null)

    whenever(calculationTransactionalService.validateAndCalculate(any(), any(), any(), any(), any(), any())).thenReturn(
      validationResult,
    )

    whenever(calculationReasonRepository.findTopByIsBulkTrue()).thenReturn(Optional.of(BULK_CALCULATION_REASON))

    val mismatch = bulkComparisonService.buildMismatch(calculableSentenceEnvelope)

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

    whenever(calculationReasonRepository.findTopByIsBulkTrue()).thenReturn(Optional.of(BULK_CALCULATION_REASON))

    val mismatch = bulkComparisonService.buildMismatch(sexOffenderCalculableSentenceEnvelope)

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

    whenever(calculationReasonRepository.findTopByIsBulkTrue()).thenReturn(Optional.of(BULK_CALCULATION_REASON))

    val notHdc4SentenceTypeEnvelope = calculableSentenceEnvelope.copy(
      sentenceAndOffences = listOf(sentenceAndOffence.copy(sentenceCalculationType = SentenceCalculationType.SEC236A.name)),
    )
    val mismatch = bulkComparisonService.buildMismatch(notHdc4SentenceTypeEnvelope)

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

    whenever(calculationReasonRepository.findTopByIsBulkTrue()).thenReturn(Optional.of(BULK_CALCULATION_REASON))

    val threeYearSentenceEnvelope = calculableSentenceEnvelope.copy(
      sentenceAndOffences = listOf(sentenceAndOffence.copy(terms = listOf(SentenceTerms(years = 3)))),
    )
    val mismatch = bulkComparisonService.buildMismatch(threeYearSentenceEnvelope)

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

    whenever(calculationReasonRepository.findTopByIsBulkTrue()).thenReturn(Optional.of(BULK_CALCULATION_REASON))

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

    val mismatch = bulkComparisonService.buildMismatch(consecutiveSentencesEnvelope)

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

    whenever(calculationReasonRepository.findTopByIsBulkTrue()).thenReturn(Optional.of(BULK_CALCULATION_REASON))

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

    val mismatch = bulkComparisonService.buildMismatch(consecutiveSentencesEnvelope)

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

    whenever(calculationReasonRepository.findTopByIsBulkTrue()).thenReturn(Optional.of(BULK_CALCULATION_REASON))

    val mismatch = bulkComparisonService.buildMismatch(calculableSentenceEnvelope)

    assertFalse(mismatch.isValid)
    assertFalse(mismatch.isMatch)
    assertEquals(MismatchType.VALIDATION_ERROR_HDC4_PLUS, mismatch.type)
  }

  @Test
  fun `Determine if a mismatch report is not valid due to unsupported sentence type`() {
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

    whenever(calculationReasonRepository.findTopByIsBulkTrue()).thenReturn(Optional.of(BULK_CALCULATION_REASON))

    val indeterminateSentenceEnvelope = calculableSentenceEnvelope.copy(
      sentenceAndOffences = listOf(sentenceAndOffence.copy(sentenceCalculationType = "LIFE")),
    )
    val mismatch = bulkComparisonService.buildMismatch(indeterminateSentenceEnvelope)

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

    whenever(calculationReasonRepository.findTopByIsBulkTrue()).thenReturn(Optional.of(BULK_CALCULATION_REASON))

    val mismatch = bulkComparisonService.buildMismatch(calculableSentenceEnvelope)

    assertFalse(mismatch.isValid)
    assertFalse(mismatch.isMatch)
    assertEquals(MismatchType.UNSUPPORTED_SENTENCE_TYPE_FOR_HDC4_PLUS, mismatch.type)
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

    whenever(calculationReasonRepository.findTopByIsBulkTrue()).thenReturn(Optional.of(BULK_CALCULATION_REASON))

    val indeterminateSentenceEnvelope = calculableSentenceEnvelope.copy(
      sentenceAndOffences = listOf(sentenceAndOffence.copy(sentenceCalculationType = "LIFE")),
    )
    val mismatch = bulkComparisonService.buildMismatch(indeterminateSentenceEnvelope)

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
      calculationReason = BULK_CALCULATION_REASON,
    )

    val booking =
      Booking(Offender("a", LocalDate.of(1980, 1, 1), true), emptyList(), Adjustments(), null, null, 123, true)
    val validationResult = ValidationResult(emptyList(), booking, duplicatedReleaseDates, null)

    whenever(calculationTransactionalService.validateAndCalculate(any(), any(), any(), any(), any(), any())).thenReturn(
      validationResult,
    )

    whenever(calculationReasonRepository.findTopByIsBulkTrue()).thenReturn(Optional.of(BULK_CALCULATION_REASON))

    val mismatch = bulkComparisonService.buildMismatch(calculableSentenceEnvelope)

    assertTrue(mismatch.isValid)
    assertFalse(mismatch.isMatch)
    assertEquals(MismatchType.RELEASE_DATES_MISMATCH, mismatch.type)
  }

  @Test
  fun `Should set HDCED4PLUS date if not the same as HDCED`() {
    val comparison = Comparison(
      1, UUID.randomUUID(), "ABCD1234", objectMapper.createObjectNode(), "BMI", ComparisonType.ESTABLISHMENT_FULL, LocalDateTime.now(), "SOMEONE",
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
      calculationReason = BULK_CALCULATION_REASON,
    )

    val booking =
      Booking(Offender("a", LocalDate.of(1980, 1, 1), true), emptyList(), Adjustments(), null, null, 123, true)
    val validationResult = ValidationResult(emptyList(), booking, duplicatedReleaseDates, null)
    whenever(calculationTransactionalService.validateAndCalculate(any(), any(), any(), any(), any(), any())).thenReturn(
      validationResult,
    )

    whenever(calculationReasonRepository.findTopByIsBulkTrue()).thenReturn(Optional.of(BULK_CALCULATION_REASON))

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
      1, UUID.randomUUID(), "ABCD1234", objectMapper.createObjectNode(), "BMI", ComparisonType.ESTABLISHMENT_FULL, LocalDateTime.now(), "SOMEONE",
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
      calculationReason = BULK_CALCULATION_REASON,
    )

    val booking =
      Booking(Offender("a", LocalDate.of(1980, 1, 1), true), emptyList(), Adjustments(), null, null, 123, true)
    val validationResult = ValidationResult(emptyList(), booking, duplicatedReleaseDates, null)
    whenever(calculationTransactionalService.validateAndCalculate(any(), any(), any(), any(), any(), any())).thenReturn(
      validationResult,
    )

    whenever(calculationReasonRepository.findTopByIsBulkTrue()).thenReturn(Optional.of(BULK_CALCULATION_REASON))

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

  private val BULK_CALCULATION_REASON = CalculationReason(1, true, false, "Bulk Calculation", true)

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
}
