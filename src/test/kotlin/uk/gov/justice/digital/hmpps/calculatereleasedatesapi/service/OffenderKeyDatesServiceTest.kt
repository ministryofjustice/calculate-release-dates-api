package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import arrow.core.left
import arrow.core.right
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.anyList
import org.mockito.Mockito.mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.FeatureToggles
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationReason
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationStatus
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.CrdWebException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationContext
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.DetailedDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.GenuineOverrideReason
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.NomisCalculationReason
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.NomisCalculationSummary
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.OffenderKeyDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ReleaseDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ReleaseDateHint
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ReleaseDatesAndCalculationContext
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationOutcomeHistoricOverrideRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationRequestRepository
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
open class OffenderKeyDatesServiceTest {

  private val calculationResultEnrichmentService: CalculationResultEnrichmentService = mock(CalculationResultEnrichmentService::class.java)
  private val prisonService: PrisonService = mock(PrisonService::class.java)
  private val calculationRequestRepository: CalculationRequestRepository = mock(CalculationRequestRepository::class.java)
  private val calculationOutcomeHistoricOverrideRepository: CalculationOutcomeHistoricOverrideRepository = mock(CalculationOutcomeHistoricOverrideRepository::class.java)
  private lateinit var underTest: OffenderKeyDatesService

  @BeforeEach
  fun setUp() {
    underTest = OffenderKeyDatesService(
      prisonService,
      calculationResultEnrichmentService,
      calculationRequestRepository,
      calculationOutcomeHistoricOverrideRepository,
      FeatureToggles(historicSled = true),
    )
  }

  private val now = LocalDateTime.now()
  val reference: UUID = UUID.randomUUID()

  @Test
  fun `Test getting NomisCalculationSummary for offenderSentCalcId successfully`() {
    val offenderSentCalcId = 5636121L
    val offenderKeyDates = OffenderKeyDates(
      reasonCode = "FS",
      calculatedAt = LocalDateTime.of(2024, 2, 29, 10, 30),
      comment = null,
      homeDetentionCurfewEligibilityDate = LocalDate.of(2024, 1, 1),
    )
    val expected = NomisCalculationSummary(
      "Further Sentence",
      LocalDateTime.of(2024, 2, 29, 10, 30),
      null,
      listOf(
        DetailedDate(
          ReleaseDateType.HDCED,
          ReleaseDateType.HDCED.description,
          LocalDate.of(2024, 1, 1),
          emptyList(),
        ),
      ),
    )

    val detailedDates = mapOf(
      ReleaseDateType.HDCED to DetailedDate(
        ReleaseDateType.HDCED,
        ReleaseDateType.HDCED.description,
        LocalDate.of(2024, 1, 1),
        emptyList(),
      ),
    )

    whenever(prisonService.getNOMISOffenderKeyDates(any())).thenReturn(offenderKeyDates.right())
    whenever(
      calculationResultEnrichmentService.addDetailToCalculationDates(
        anyList(),
        isNull(),
        isNull(),
        isNull(),
        eq(offenderKeyDates),
        isNull(),
      ),
    ).thenReturn(detailedDates)
    whenever(prisonService.getNOMISCalcReasons()).thenReturn(
      listOf(
        NomisCalculationReason(
          code = "FS",
          description = "Further Sentence",
        ),
      ),
    )

    val result = underTest.getNomisCalculationSummary(offenderSentCalcId)

    assertThat(result.reason).isEqualTo(expected.reason)
    assertThat(result.calculatedAt).isEqualTo(expected.calculatedAt)
    assertThat(result.comment).isEqualTo(expected.comment)
    assertThat(result.releaseDates).isEqualTo(expected.releaseDates)
  }

  @Test
  fun `Test getting Release Dates for calc request id successfully`() {
    val bookingId = 5636121L
    val calcRequestId = 1L
    val offenderKeyDates = OffenderKeyDates(
      reasonCode = "FS",
      calculatedAt = LocalDateTime.of(2024, 2, 29, 10, 30),
      comment = null,
      homeDetentionCurfewEligibilityDate = LocalDate.of(2024, 1, 1),
    )
    val expected = ReleaseDatesAndCalculationContext(
      CalculationContext(
        calcRequestId,
        bookingId,
        "A1234AB",
        CalculationStatus.CONFIRMED,
        reference,
        CalculationReason(-1, false, false, "14 day check", false, null, null, 1),
        null,
        LocalDate.of(2024, 1, 1),
        CalculationType.CALCULATED,
        null,
        null,
      ),
      listOf(
        DetailedDate(
          ReleaseDateType.HDCED,
          ReleaseDateType.HDCED.description,
          LocalDate.of(2024, 1, 1),
          emptyList(),
        ),
      ),
    )

    val detailedDates = mapOf(
      ReleaseDateType.HDCED to DetailedDate(
        ReleaseDateType.HDCED,
        ReleaseDateType.HDCED.description,
        LocalDate.of(2024, 1, 1),
        emptyList(),
      ),
    )
    val calcRequest = CalculationRequest(
      1,
      reference,
      "A1234AB",
      bookingId,
      CalculationStatus.CONFIRMED.name,
      calculatedAt = LocalDateTime.of(2024, 1, 1, 0, 0),
      reasonForCalculation = CalculationReason(
        -1,
        false,
        false,
        "14 day check",
        false,
        null,
        null,
        1,
      ),
      otherReasonForCalculation = null,
      calculationType = CalculationType.CALCULATED,
    )

    whenever(prisonService.getOffenderKeyDates(any())).thenReturn(offenderKeyDates.right())
    whenever(calculationRequestRepository.findById(calcRequestId)).thenReturn(Optional.of(calcRequest))
    whenever(
      calculationResultEnrichmentService.addDetailToCalculationDates(
        anyList(),
        isNull(),
        isNull(),
        isNull(),
        isNull(),
        isNull(),
      ),
    ).thenReturn(detailedDates)

    val result = underTest.getKeyDatesByCalcId(calcRequestId)

    assertThat(result).isEqualTo(expected)
  }

  @Test
  fun `Test historic SLED`() {
    val bookingId = 5636121L
    val calcRequestId = 1L
    val offenderKeyDates = OffenderKeyDates(
      reasonCode = "FS",
      calculatedAt = LocalDateTime.of(2024, 2, 29, 10, 30),
      comment = null,
      licenceExpiryDate = LocalDate.of(2024, 1, 1),
      sentenceExpiryDate = LocalDate.of(2024, 1, 1),
    )
    val expected = ReleaseDatesAndCalculationContext(
      CalculationContext(
        calcRequestId,
        bookingId,
        "A1234AB",
        CalculationStatus.CONFIRMED,
        reference,
        CalculationReason(-1, false, false, "SLED test", false, null, null, 1),
        null,
        LocalDate.of(2024, 1, 1),
        CalculationType.CALCULATED,
        null,
        null,
      ),
      listOf(
        DetailedDate(
          ReleaseDateType.SLED,
          ReleaseDateType.SLED.description,
          LocalDate.of(2024, 1, 1),
          listOf(ReleaseDateHint("SLED from a previous period of custody")),
        ),
      ),
    )
    val detailedDates = mapOf(
      ReleaseDateType.SLED to DetailedDate(
        ReleaseDateType.SLED,
        ReleaseDateType.SLED.description,
        LocalDate.of(2024, 1, 1),
        listOf(ReleaseDateHint("SLED from a previous period of custody")),
      ),
    )
    val calcRequest = CalculationRequest(
      1,
      reference,
      "A1234AB",
      bookingId,
      CalculationStatus.CONFIRMED.name,
      calculatedAt = LocalDateTime.of(2024, 1, 1, 0, 0),
      reasonForCalculation = CalculationReason(
        -1,
        false,
        false,
        "SLED test",
        false,
        null,
        null,
        1,
      ),
      otherReasonForCalculation = null,
      calculationType = CalculationType.CALCULATED,
    )

    whenever(prisonService.getOffenderKeyDates(any())).thenReturn(offenderKeyDates.right())
    whenever(calculationRequestRepository.findById(calcRequestId)).thenReturn(Optional.of(calcRequest))
    whenever(
      calculationResultEnrichmentService.addDetailToCalculationDates(
        anyList(),
        isNull(),
        isNull(),
        isNull(),
        isNull(),
        isNull(),
      ),
    ).thenReturn(detailedDates)

    val result = underTest.getKeyDatesByCalcId(calcRequestId)

    assertThat(result).isEqualTo(expected)
  }

  @Test
  fun `Test getting Release Dates for calc request id for exception scenario calcRequest is in error`() {
    val calcRequestId = 5636121L
    val errorMessage = "Unable to retrieve offender key dates"

    whenever(calculationRequestRepository.findById(calcRequestId)).thenThrow(NoSuchElementException("Database error"))

    val exception = assertThrows<CrdWebException> {
      underTest.getKeyDatesByCalcId(calcRequestId)
    }

    assertThat(exception.message).isEqualTo(errorMessage)
  }

  @Test
  fun `Test getting Release Dates for calc request id for exception scenario getOffenderKeyDates is in error`() {
    val calcRequestId = 5636121L
    val bookingId = 56121L
    val errorMessage = "Unable to retrieve offender key dates"
    val calcRequest = CalculationRequest(
      1,
      reference,
      "A1234AB",
      bookingId,
      CalculationStatus.CONFIRMED.name,
      calculatedAt = LocalDateTime.of(2024, 1, 1, 0, 0),
      reasonForCalculation = CalculationReason(
        -1,
        false,
        false,
        "14 day check",
        false,
        null,
        null,
        1,
      ),
      otherReasonForCalculation = null,
      calculationType = CalculationType.CALCULATED,
    )

    whenever(prisonService.getOffenderKeyDates(any())).thenReturn(errorMessage.left())
    whenever(calculationRequestRepository.findById(calcRequestId)).thenReturn(Optional.of(calcRequest))

    val exception = assertThrows<CrdWebException> {
      underTest.getKeyDatesByCalcId(calcRequestId)
    }

    assertThat(exception.message).isEqualTo(errorMessage)
  }

  @Test
  fun `Test getting Release Dates for calc request id for exception scenario addDetailToCalculationDates is in error`() {
    val calcRequestId = 5636121L
    val bookingId = 56121L
    val errorMessage = "Unable to retrieve offender key dates"
    val offenderKeyDates = OffenderKeyDates(
      reasonCode = "FS",
      calculatedAt = LocalDateTime.of(2024, 2, 29, 10, 30),
      comment = null,
      homeDetentionCurfewEligibilityDate = LocalDate.of(2024, 1, 1),
    )
    val calcRequest = CalculationRequest(
      1,
      reference,
      "A1234AB",
      bookingId,
      CalculationStatus.CONFIRMED.name,
      calculatedAt = LocalDateTime.of(2024, 1, 1, 0, 0),
      reasonForCalculation = CalculationReason(
        -1,
        false,
        false,
        "14 day check",
        false,
        null,
        null,
        1,
      ),
      otherReasonForCalculation = null,
      calculationType = CalculationType.CALCULATED,
    )

    whenever(prisonService.getOffenderKeyDates(any())).thenReturn(offenderKeyDates.right())
    whenever(calculationRequestRepository.findById(calcRequestId)).thenReturn(Optional.of(calcRequest))
    whenever(
      calculationResultEnrichmentService.addDetailToCalculationDates(
        anyList(),
        isNull(),
        isNull(),
        isNull(),
        isNull(),
        isNull(),
      ),
    )
      .thenThrow(NoSuchElementException("Error"))

    val exception = assertThrows<CrdWebException> {
      underTest.getKeyDatesByCalcId(calcRequestId)
    }

    assertThat(exception.message).isEqualTo(errorMessage)
  }

  @Test
  fun `Test NomisCalculationSummary returns Nomis override dates`() {
    val offenderSentCalcId = 5636121L
    val offenderKeyDates = OffenderKeyDates(
      reasonCode = "FS",
      calculatedAt = LocalDateTime.of(2024, 2, 29, 10, 30),
      comment = null,
      conditionalReleaseDate = LocalDate.of(2024, 1, 1),
      homeDetentionCurfewEligibilityDate = LocalDate.of(2024, 1, 2),
    )

    val detailedDates = mapOf(
      ReleaseDateType.CRD to DetailedDate(
        ReleaseDateType.CRD,
        ReleaseDateType.CRD.description,
        offenderKeyDates.conditionalReleaseDate!!,
        listOf(ReleaseDateHint("Manually overridden")),
      ),
      ReleaseDateType.HDCED to DetailedDate(
        ReleaseDateType.HDCED,
        ReleaseDateType.HDCED.description,
        offenderKeyDates.homeDetentionCurfewEligibilityDate!!,
        emptyList(),
      ),
    )

    whenever(prisonService.getNOMISOffenderKeyDates(any())).thenReturn(offenderKeyDates.right())
    whenever(
      calculationResultEnrichmentService.addDetailToCalculationDates(
        anyList(),
        isNull(),
        isNull(),
        isNull(),
        eq(offenderKeyDates),
        isNull(),
      ),
    ).thenReturn(detailedDates)

    val result = underTest.getNomisCalculationSummary(offenderSentCalcId)
    assertThat(result.releaseDates.size).isEqualTo(2)
    val crdDate = result.releaseDates.find { it.type == ReleaseDateType.CRD }
    val hdcDate = result.releaseDates.find { it.type == ReleaseDateType.HDCED }

    assertThat(result.releaseDates.size).isEqualTo(2)
    assertThat(crdDate!!.hints.contains(ReleaseDateHint("Manually overridden")))
    assertThat(hdcDate!!.hints.none { it == ReleaseDateHint("Manually overridden") })
  }

  @Test
  fun `Test NomisCalculationSummary not existing code reason code`() {
    val offenderSentCalcId = 5636121L
    val offenderKeyDates = OffenderKeyDates(
      reasonCode = "FS",
      calculatedAt = LocalDateTime.of(2024, 2, 29, 10, 30),
      comment = null,
    )

    whenever(prisonService.getNOMISOffenderKeyDates(any())).thenReturn(offenderKeyDates.right())

    val result = underTest.getNomisCalculationSummary(offenderSentCalcId)

    assertThat(result.reason).isEqualTo("FS")
  }

  @Test
  fun `Test NomisCalculationSummary for exception scenario`() {
    val offenderSentCalcId = 5636121L
    val errorMessage = "There isn't one"

    whenever(prisonService.getNOMISOffenderKeyDates(any())).thenReturn(errorMessage.left())

    val exception = assertThrows<CrdWebException> {
      underTest.getNomisCalculationSummary(offenderSentCalcId)
    }

    assertThat(exception.message).isEqualTo(errorMessage)
  }

  @Test
  fun `Should replace SED and LED with SLED if SED and LED are the same`() {
    val offenderKeyDates = OffenderKeyDates(
      sentenceExpiryDate = LocalDate.of(2025, 1, 1),
      licenceExpiryDate = LocalDate.of(2025, 1, 1),
      reasonCode = "NEW",
      calculatedAt = now,
    )

    val generatedDates = underTest.releaseDates(offenderKeyDates)

    assertThat(generatedDates).anyMatch { it.type == ReleaseDateType.SLED && it.date == LocalDate.of(2025, 1, 1) }
    assertThat(generatedDates).noneMatch { it.type == ReleaseDateType.SED || it.type == ReleaseDateType.LED }
  }

  @Test
  fun `Should create SED and LED and not SLED if SED and LED are the different`() {
    val offenderKeyDates = OffenderKeyDates(
      sentenceExpiryDate = LocalDate.of(2025, 1, 1),
      licenceExpiryDate = LocalDate.of(2026, 1, 1),
      reasonCode = "NEW",
      calculatedAt = now,
    )

    val generatedDates = underTest.releaseDates(offenderKeyDates)

    assertThat(generatedDates).noneMatch { it.type == ReleaseDateType.SLED }
    assertThat(generatedDates).anyMatch { it.type == ReleaseDateType.SED && it.date == LocalDate.of(2025, 1, 1) }
    assertThat(generatedDates).anyMatch { it.type == ReleaseDateType.LED && it.date == LocalDate.of(2026, 1, 1) }
  }

  @Test
  fun `Should not create SLED if SED and LED are both absent`() {
    val offenderKeyDates =
      OffenderKeyDates(
        reasonCode = "NEW",
        calculatedAt = now,
      )

    val generatedDates = underTest.releaseDates(offenderKeyDates)

    assertThat(generatedDates).noneMatch { it.type == ReleaseDateType.SLED }
    assertThat(generatedDates).noneMatch { it.type == ReleaseDateType.SED || it.type == ReleaseDateType.LED }
  }

  @Test
  fun `Should map all possible NOMIS dates`() {
    val offenderKeyDates =
      OffenderKeyDates(
        sentenceExpiryDate = LocalDate.of(2025, 1, 1),
        licenceExpiryDate = LocalDate.of(2025, 1, 2),
        paroleEligibilityDate = LocalDate.of(2025, 1, 3),
        homeDetentionCurfewEligibilityDate = LocalDate.of(2025, 1, 4),
        homeDetentionCurfewApprovedDate = LocalDate.of(2025, 1, 5),
        automaticReleaseDate = LocalDate.of(2025, 1, 6),
        conditionalReleaseDate = LocalDate.of(2025, 1, 7),
        nonParoleDate = LocalDate.of(2025, 1, 8),
        postRecallReleaseDate = LocalDate.of(2025, 1, 9),
        approvedParoleDate = LocalDate.of(2025, 1, 10),
        topupSupervisionExpiryDate = LocalDate.of(2025, 1, 11),
        earlyTermDate = LocalDate.of(2025, 1, 12),
        midTermDate = LocalDate.of(2025, 1, 13),
        lateTermDate = LocalDate.of(2025, 1, 14),
        tariffDate = LocalDate.of(2025, 1, 15),
        releaseOnTemporaryLicenceDate = LocalDate.of(2025, 1, 16),
        earlyRemovalSchemeEligibilityDate = LocalDate.of(2025, 1, 17),
        tariffExpiredRemovalSchemeEligibilityDate = LocalDate.of(2025, 1, 18),
        dtoPostRecallReleaseDate = LocalDate.of(2025, 1, 19),
        reasonCode = "NEW",
        calculatedAt = now,
      )

    val dates = listOf(
      ReleaseDate(LocalDate.of(2025, 1, 1), ReleaseDateType.SED),
      ReleaseDate(LocalDate.of(2025, 1, 2), ReleaseDateType.LED),
      ReleaseDate(LocalDate.of(2025, 1, 7), ReleaseDateType.CRD),
      ReleaseDate(LocalDate.of(2025, 1, 6), ReleaseDateType.ARD),
      ReleaseDate(LocalDate.of(2025, 1, 4), ReleaseDateType.HDCED),
      ReleaseDate(LocalDate.of(2025, 1, 11), ReleaseDateType.TUSED),
      ReleaseDate(LocalDate.of(2025, 1, 9), ReleaseDateType.PRRD),
      ReleaseDate(LocalDate.of(2025, 1, 3), ReleaseDateType.PED),
      ReleaseDate(LocalDate.of(2025, 1, 16), ReleaseDateType.ROTL),
      ReleaseDate(LocalDate.of(2025, 1, 17), ReleaseDateType.ERSED),
      ReleaseDate(LocalDate.of(2025, 1, 5), ReleaseDateType.HDCAD),
      ReleaseDate(LocalDate.of(2025, 1, 13), ReleaseDateType.MTD),
      ReleaseDate(LocalDate.of(2025, 1, 12), ReleaseDateType.ETD),
      ReleaseDate(LocalDate.of(2025, 1, 14), ReleaseDateType.LTD),
      ReleaseDate(LocalDate.of(2025, 1, 10), ReleaseDateType.APD),
      ReleaseDate(LocalDate.of(2025, 1, 8), ReleaseDateType.NPD),
      ReleaseDate(LocalDate.of(2025, 1, 19), ReleaseDateType.DPRRD),
      ReleaseDate(LocalDate.of(2025, 1, 15), ReleaseDateType.Tariff),
      ReleaseDate(LocalDate.of(2025, 1, 18), ReleaseDateType.TERSED),
    )

    val result = underTest.releaseDates(offenderKeyDates)

    assertThat(dates).isEqualTo(result)
  }

  @Test
  fun `Get release dates for genuine override not OTHER successfully`() {
    val bookingId = 5636121L
    val calcRequestId = 1L
    val offenderKeyDates = OffenderKeyDates(
      reasonCode = "FS",
      calculatedAt = LocalDateTime.of(2024, 2, 29, 10, 30),
      comment = null,
      homeDetentionCurfewEligibilityDate = LocalDate.of(2024, 1, 1),
    )
    val expected = ReleaseDatesAndCalculationContext(
      CalculationContext(
        calcRequestId,
        bookingId,
        "A1234AB",
        CalculationStatus.CONFIRMED,
        reference,
        CalculationReason(-1, false, false, "14 day check", false, null, null, 1),
        null,
        LocalDate.of(2024, 1, 1),
        CalculationType.GENUINE_OVERRIDE,
        GenuineOverrideReason.AGGRAVATING_FACTOR_OFFENCE,
        "One or more offences have been characterised by an aggravating factor (such as terror)",
      ),
      listOf(
        DetailedDate(
          ReleaseDateType.HDCED,
          ReleaseDateType.HDCED.description,
          LocalDate.of(2024, 1, 1),
          emptyList(),
        ),
      ),
    )

    val detailedDates = mapOf(
      ReleaseDateType.HDCED to DetailedDate(
        ReleaseDateType.HDCED,
        ReleaseDateType.HDCED.description,
        LocalDate.of(2024, 1, 1),
        emptyList(),
      ),
    )
    val calcRequest = CalculationRequest(
      1,
      reference,
      "A1234AB",
      bookingId,
      CalculationStatus.CONFIRMED.name,
      calculatedAt = LocalDateTime.of(2024, 1, 1, 0, 0),
      reasonForCalculation = CalculationReason(
        -1,
        false,
        false,
        "14 day check",
        false,
        null,
        null,
        1,
      ),
      otherReasonForCalculation = null,
      calculationType = CalculationType.GENUINE_OVERRIDE,
      genuineOverrideReason = GenuineOverrideReason.AGGRAVATING_FACTOR_OFFENCE,
      genuineOverrideReasonFurtherDetail = null,
    )

    whenever(prisonService.getOffenderKeyDates(any())).thenReturn(offenderKeyDates.right())
    whenever(calculationRequestRepository.findById(calcRequestId)).thenReturn(Optional.of(calcRequest))
    whenever(
      calculationResultEnrichmentService.addDetailToCalculationDates(
        anyList(),
        isNull(),
        isNull(),
        isNull(),
        isNull(),
        isNull(),
      ),
    ).thenReturn(detailedDates)

    val result = underTest.getKeyDatesByCalcId(calcRequestId)

    assertThat(result).isEqualTo(expected)
  }

  @Test
  fun `Get release dates for genuine override OTHER successfully`() {
    val bookingId = 5636121L
    val calcRequestId = 1L
    val offenderKeyDates = OffenderKeyDates(
      reasonCode = "FS",
      calculatedAt = LocalDateTime.of(2024, 2, 29, 10, 30),
      comment = null,
      homeDetentionCurfewEligibilityDate = LocalDate.of(2024, 1, 1),
    )
    val expected = ReleaseDatesAndCalculationContext(
      CalculationContext(
        calcRequestId,
        bookingId,
        "A1234AB",
        CalculationStatus.CONFIRMED,
        reference,
        CalculationReason(-1, false, false, "14 day check", false, null, null, 1),
        null,
        LocalDate.of(2024, 1, 1),
        CalculationType.GENUINE_OVERRIDE,
        GenuineOverrideReason.OTHER,
        "Some extra detail",
      ),
      listOf(
        DetailedDate(
          ReleaseDateType.HDCED,
          ReleaseDateType.HDCED.description,
          LocalDate.of(2024, 1, 1),
          emptyList(),
        ),
      ),
    )

    val detailedDates = mapOf(
      ReleaseDateType.HDCED to DetailedDate(
        ReleaseDateType.HDCED,
        ReleaseDateType.HDCED.description,
        LocalDate.of(2024, 1, 1),
        emptyList(),
      ),
    )
    val calcRequest = CalculationRequest(
      1,
      reference,
      "A1234AB",
      bookingId,
      CalculationStatus.CONFIRMED.name,
      calculatedAt = LocalDateTime.of(2024, 1, 1, 0, 0),
      reasonForCalculation = CalculationReason(
        -1,
        false,
        false,
        "14 day check",
        false,
        null,
        null,
        1,
      ),
      otherReasonForCalculation = null,
      calculationType = CalculationType.GENUINE_OVERRIDE,
      genuineOverrideReason = GenuineOverrideReason.OTHER,
      genuineOverrideReasonFurtherDetail = "Some extra detail",
    )

    whenever(prisonService.getOffenderKeyDates(any())).thenReturn(offenderKeyDates.right())
    whenever(calculationRequestRepository.findById(calcRequestId)).thenReturn(Optional.of(calcRequest))
    whenever(
      calculationResultEnrichmentService.addDetailToCalculationDates(
        anyList(),
        isNull(),
        isNull(),
        isNull(),
        isNull(),
        isNull(),
      ),
    ).thenReturn(detailedDates)

    val result = underTest.getKeyDatesByCalcId(calcRequestId)

    assertThat(result).isEqualTo(expected)
  }
}
