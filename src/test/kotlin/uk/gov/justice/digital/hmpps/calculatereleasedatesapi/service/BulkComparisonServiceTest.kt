package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationStatus
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Adjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculatedReleaseDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offender
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi.Agency
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi.CalculableSentenceEnvelope
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi.CourtSentences
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi.KeyDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi.OffenderOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi.Person
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi.PrisonTerm
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi.SentencesOffencesTerms
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.ComparisonPersonRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationResult
import java.time.LocalDate

@ExtendWith(MockitoExtension::class)
class BulkComparisonServiceTest : IntegrationTestBase() {

  @InjectMocks
  lateinit var bulkComparisonService: BulkComparisonService

  private var comparisonPersonRepository = mock<ComparisonPersonRepository>()
  private var prisonService = mock<PrisonService>()
  private var calculationTransactionalService = mock<CalculationTransactionalService>()

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

    val calculatedReleaseDates = CalculatedReleaseDates(releaseDates, 123, 123, "ABC123DEF", CalculationStatus.CONFIRMED)

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
    val sentencesOffencesTerms = SentencesOffencesTerms(1, 1, "ACTIVE", "SEN", "TYPE", "DESC", LocalDate.of(2010, 1, 1), LocalDate.of(2013, 1, 1), 0.toDouble(), 2, listOf(offenderOffence), null)
    val courtSentence = CourtSentences("12", 123, 0, LocalDate.of(2012, 1, 1), Agency("MYC", "My Court", "My Court Name", "PRI", true), "CASE", "SWAG", "ACTIVE", sentences = listOf(sentencesOffencesTerms), null, null)
    val calculableSentenceEnvelope = CalculableSentenceEnvelope(
      person = Person("A", LocalDate.of(1990, 5, 1)),
      latestPrisonTerm = PrisonTerm(
        bookingId = 12345,
        bookNumber = "ABC123",
        courtSentences = listOf(courtSentence),
        licenceSentences = listOf(sentencesOffencesTerms),
        keyDates = KeyDates(
          LocalDate.of(2012, 1, 1),
          LocalDate.of(2012, 1, 1),
          1,
          LocalDate.of(2012, 1, 1),
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          LocalDate.of(2012, 1, 1),
          null,
          null,
          null,
          null, null, null, null, null, null, null, null, null,
        ),

      ),
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

    val calculatedReleaseDates = CalculatedReleaseDates(releaseDates, 123, 123, "ABC123DEF", CalculationStatus.CONFIRMED)
    val duplicatedReleaseDates = CalculatedReleaseDates(duplicateReleaseDates, 123, 123, "ABC123DEF", CalculationStatus.CONFIRMED)

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
    val sentencesOffencesTerms = SentencesOffencesTerms(1, 1, "ACTIVE", "SEN", "TYPE", "DESC", LocalDate.of(2010, 1, 1), LocalDate.of(2013, 1, 1), 0.toDouble(), 2, listOf(offenderOffence), null)
    val courtSentence = CourtSentences("12", 123, 0, LocalDate.of(2012, 1, 1), Agency("MYC", "My Court", "My Court Name", "PRI", true), "CASE", "SWAG", "ACTIVE", sentences = listOf(sentencesOffencesTerms), null, null)
    val calculableSentenceEnvelope = CalculableSentenceEnvelope(
      person = Person("A", LocalDate.of(1990, 5, 1)),
      latestPrisonTerm = PrisonTerm(
        bookingId = 12345,
        bookNumber = "ABC123",
        courtSentences = listOf(courtSentence),
        licenceSentences = listOf(sentencesOffencesTerms),
        keyDates = KeyDates(
          LocalDate.of(2012, 1, 1),
          LocalDate.of(2012, 1, 1),
          1,
          LocalDate.of(2012, 1, 1),
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          LocalDate.of(2012, 1, 1),
          null,
          null,
          null,
          null, null, null, null, null, null, null, null, null,
        ),

      ),
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
