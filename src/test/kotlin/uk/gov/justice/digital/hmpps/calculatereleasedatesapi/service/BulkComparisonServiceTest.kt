package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationStatus
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Adjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculatedReleaseDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offender
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.OffenderOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAndOffences
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceTerms
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi.CalculableSentenceEnvelope
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi.Person
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.ComparisonPersonRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.ComparisonRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationResult
import java.time.LocalDate
import java.util.*

@ExtendWith(MockitoExtension::class)
class BulkComparisonServiceTest : IntegrationTestBase() {

  @InjectMocks
  lateinit var bulkComparisonService: BulkComparisonService

  private var comparisonPersonRepository = mock<ComparisonPersonRepository>()
  private var prisonService = mock<PrisonService>()
  private var calculationTransactionalService = mock<CalculationTransactionalService>()
  private var objectMapper: ObjectMapper = spy<ObjectMapper>()
  private var comparisonRepository = mock<ComparisonRepository>()

  @Test
  fun `Determine if a mismatch report  isValid and  isMatch `() {
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

    val calculatedReleaseDates = CalculatedReleaseDates(
      dates = releaseDates,
      calculationRequestId = 123,
      bookingId = 123,
      prisonerId = "ABC123DEF",
      calculationStatus = CalculationStatus.CONFIRMED,
      calculationReference = UUID.randomUUID(),
    )

    val booking = Booking(Offender("a", LocalDate.of(1980, 1, 1), true), emptyList(), Adjustments(), null, null, 123, true)
    val validationResult = ValidationResult(emptyList(), booking, calculatedReleaseDates)

    Mockito.`when`(calculationTransactionalService.validateAndCalculate(any(), any(), any(), any(), any())).thenReturn(validationResult)

    val offenderOffence = OffenderOffence(
      123,
      LocalDate.of(2012, 1, 1),
      LocalDate.of(2012, 1, 1),
      "AB123DEF",
      "finagling",
      emptyList(),
    )

    val sentenceAndOffence = SentenceAndOffences(
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
      sentenceCalculationType = "TYPE",
      sentenceTypeDescription = "DESC",
      offences = listOf(offenderOffence),
    )

    val calculableSentenceEnvelope = CalculableSentenceEnvelope(
      person = Person("A", LocalDate.of(1990, 5, 1)),
      bookingId = 12345,
      sentenceAndOffences = listOf(sentenceAndOffence),
      sentenceAdjustments = emptyList(),
      bookingAdjustments = emptyList(),
      offenderFinePayments = emptyList(),
      fixedTermRecallDetails = null,
      sentenceCalcDates = calculatedReleaseDates.toSentenceCalcDates(),
    )

    val mismatch = bulkComparisonService.determineIfMismatch(calculableSentenceEnvelope)

    Assertions.assertEquals(mismatch.isValid, true)
    Assertions.assertEquals(mismatch.isMatch, true)
  }

  @Test
  fun `Determine if a mismatch report isValid and not isMatch `() {
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

    val duplicateReleaseDates = releaseDates.toMutableMap()
    duplicateReleaseDates[ReleaseDateType.SED] = LocalDate.of(2022, 1, 1)

    val calculatedReleaseDates = CalculatedReleaseDates(
      dates = releaseDates,
      calculationRequestId = 123,
      bookingId = 123,
      prisonerId = "ABC123DEF",
      calculationStatus = CalculationStatus.CONFIRMED,
      calculationReference = UUID.randomUUID(),
    )
    val duplicatedReleaseDates = CalculatedReleaseDates(
      dates = duplicateReleaseDates,
      calculationRequestId = 123,
      bookingId = 123,
      prisonerId = "ABC123DEF",
      calculationStatus = CalculationStatus.CONFIRMED,
      calculationReference = UUID.randomUUID(),
    )

    val booking = Booking(Offender("a", LocalDate.of(1980, 1, 1), true), emptyList(), Adjustments(), null, null, 123, true)
    val validationResult = ValidationResult(emptyList(), booking, duplicatedReleaseDates)

    Mockito.`when`(calculationTransactionalService.validateAndCalculate(any(), any(), any(), any(), any())).thenReturn(validationResult)

    val offenderOffence = OffenderOffence(
      123,
      LocalDate.of(2012, 1, 1),
      LocalDate.of(2012, 1, 1),
      "AB123DEF",
      "finagling",
      emptyList(),
    )
    val sentenceAndOffence = SentenceAndOffences(
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
      sentenceCalculationType = "TYPE",
      sentenceTypeDescription = "DESC",
      offences = listOf(offenderOffence),
    )
    val calculableSentenceEnvelope = CalculableSentenceEnvelope(
      person = Person("A", LocalDate.of(1990, 5, 1)),
      bookingId = 12345,
      sentenceAndOffences = listOf(sentenceAndOffence),
      sentenceAdjustments = emptyList(),
      bookingAdjustments = emptyList(),
      offenderFinePayments = emptyList(),
      fixedTermRecallDetails = null,
      sentenceCalcDates = calculatedReleaseDates.toSentenceCalcDates(),
    )

    val mismatch = bulkComparisonService.determineIfMismatch(calculableSentenceEnvelope)

    Assertions.assertEquals(mismatch.isValid, true)
    Assertions.assertEquals(mismatch.isMatch, false)
  }
}
