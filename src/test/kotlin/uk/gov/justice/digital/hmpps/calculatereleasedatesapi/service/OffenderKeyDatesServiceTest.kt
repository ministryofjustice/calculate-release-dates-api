package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import arrow.core.left
import arrow.core.right
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.isNull
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.CrdWebException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.DetailedDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.NomisCalculationReason
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.NomisCalculationSummary
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.OffenderKeyDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ReleaseDate
import java.time.LocalDate
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
open class OffenderKeyDatesServiceTest {

  @Mock
  lateinit var calculationResultEnrichmentService: CalculationResultEnrichmentService

  @Mock
  lateinit var prisonService: PrisonService

  @InjectMocks
  lateinit var underTest: OffenderKeyDatesService

  private val now = LocalDateTime.now()

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

    val detailedDates = mapOf(ReleaseDateType.HDCED to DetailedDate(ReleaseDateType.HDCED, ReleaseDateType.HDCED.description, LocalDate.of(2024, 1, 1), emptyList()))

    whenever(prisonService.getNOMISOffenderKeyDates(any())).thenReturn(offenderKeyDates.right())
    whenever(calculationResultEnrichmentService.addDetailToCalculationDates(Mockito.anyList(), isNull(), isNull())).thenReturn(detailedDates)
    whenever(prisonService.getNOMISCalcReasons()).thenReturn(listOf(NomisCalculationReason(code = "FS", description = "Further Sentence")))

    val result = underTest.getNomisCalculationSummary(offenderSentCalcId)

    Assertions.assertThat(result.reason).isEqualTo(expected.reason)
    Assertions.assertThat(result.calculatedAt).isEqualTo(expected.calculatedAt)
    Assertions.assertThat(result.comment).isEqualTo(expected.comment)
    Assertions.assertThat(result.releaseDates).isEqualTo(expected.releaseDates)
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

    Assertions.assertThat(result.reason).isEqualTo("FS")
  }

  @Test
  fun `Test NomisCalculationSummary for exception scenario`() {
    val offenderSentCalcId = 5636121L
    val errorMessage = "There isn't one"

    whenever(prisonService.getNOMISOffenderKeyDates(any())).thenReturn(errorMessage.left())

    val exception = assertThrows<CrdWebException> {
      underTest.getNomisCalculationSummary(offenderSentCalcId)
    }

    Assertions.assertThat(exception.message).isEqualTo(errorMessage)
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

    Assertions.assertThat(generatedDates).anyMatch { it.type == ReleaseDateType.SLED && it.date == LocalDate.of(2025, 1, 1) }
    Assertions.assertThat(generatedDates).noneMatch { it.type == ReleaseDateType.SED || it.type == ReleaseDateType.LED }
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

    Assertions.assertThat(generatedDates).noneMatch { it.type == ReleaseDateType.SLED }
    Assertions.assertThat(generatedDates).anyMatch { it.type == ReleaseDateType.SED && it.date == LocalDate.of(2025, 1, 1) }
    Assertions.assertThat(generatedDates).anyMatch { it.type == ReleaseDateType.LED && it.date == LocalDate.of(2026, 1, 1) }
  }

  @Test
  fun `Should not create SLED if SED and LED are both absent`() {
    val offenderKeyDates =
      OffenderKeyDates(
        reasonCode = "NEW",
        calculatedAt = now,
      )

    val generatedDates = underTest.releaseDates(offenderKeyDates)

    Assertions.assertThat(generatedDates).noneMatch { it.type == ReleaseDateType.SLED }
    Assertions.assertThat(generatedDates).noneMatch { it.type == ReleaseDateType.SED || it.type == ReleaseDateType.LED }
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

    Assertions.assertThat(dates).isEqualTo(result)
  }
}
