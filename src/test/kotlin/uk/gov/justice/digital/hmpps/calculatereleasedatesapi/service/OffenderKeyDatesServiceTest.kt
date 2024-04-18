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
}
