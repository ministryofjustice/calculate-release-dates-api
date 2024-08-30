package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AdjustmentAnalysisResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BookingAdjustment
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BookingAdjustmentType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BookingAndSentenceAdjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAdjustment
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAdjustmentType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationRequestRepository
import java.time.LocalDate
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class AdjustmentsServiceTest {

  @Mock
  lateinit var prisonService: PrisonService

  @Mock
  lateinit var calculationRequestRepository: CalculationRequestRepository

  @InjectMocks
  lateinit var underTest: AdjustmentsService

  @Test
  fun `All adjustments are new`() {
    val bookingAndOffenceAdjustments = BookingAndSentenceAdjustments(listOf(BookingAdjustment(true, LocalDate.MIN, null, 3, BookingAdjustmentType.ADDITIONAL_DAYS_AWARDED)), emptyList())
    whenever(prisonService.getBookingAndSentenceAdjustments(anyLong(), anyBoolean())).thenReturn(bookingAndOffenceAdjustments)
    whenever(calculationRequestRepository.findFirstByBookingIdOrderByCalculatedAtDesc(anyLong())).thenReturn(Optional.empty())
    val analyzedAdjustments = underTest.getAnalyzedAdjustments(123)
    assertThat(analyzedAdjustments).isNotNull
    assertThat(analyzedAdjustments.bookingAdjustments).hasSize(1)
    assertThat(analyzedAdjustments.bookingAdjustments[0].analysisResult).isEqualTo(AdjustmentAnalysisResult.NEW)
  }

  @Test
  fun `All adjustments are same`() {
    val bookingAndOffenceAdjustments = BookingAndSentenceAdjustments(listOf(BookingAdjustment(true, LocalDate.MIN, null, 3, BookingAdjustmentType.ADDITIONAL_DAYS_AWARDED)), emptyList())
    whenever(prisonService.getBookingAndSentenceAdjustments(anyLong(), anyBoolean())).thenReturn(bookingAndOffenceAdjustments)
    whenever(calculationRequestRepository.findFirstByBookingIdOrderByCalculatedAtDesc(anyLong())).thenReturn(Optional.of(CalculationRequest(adjustments = objectToJson(bookingAndOffenceAdjustments, jacksonObjectMapper().findAndRegisterModules()))))
    val analyzedAdjustments = underTest.getAnalyzedAdjustments(123)
    assertThat(analyzedAdjustments).isNotNull
    assertThat(analyzedAdjustments.bookingAdjustments).hasSize(1)
    assertThat(analyzedAdjustments.bookingAdjustments[0].analysisResult).isEqualTo(AdjustmentAnalysisResult.SAME)
  }

  @Test
  fun `There is a new adjustment`() {
    val bookingAndOffenceAdjustments = BookingAndSentenceAdjustments(listOf(BookingAdjustment(true, LocalDate.MIN, null, 3, BookingAdjustmentType.ADDITIONAL_DAYS_AWARDED)), listOf(SentenceAdjustment(1, true, LocalDate.MIN, null, 5, SentenceAdjustmentType.TAGGED_BAIL)))
    val noSentenceAdjustment = BookingAndSentenceAdjustments(listOf(BookingAdjustment(true, LocalDate.MIN, null, 3, BookingAdjustmentType.ADDITIONAL_DAYS_AWARDED)), emptyList())
    whenever(prisonService.getBookingAndSentenceAdjustments(anyLong(), anyBoolean())).thenReturn(bookingAndOffenceAdjustments)
    whenever(calculationRequestRepository.findFirstByBookingIdOrderByCalculatedAtDesc(anyLong())).thenReturn(Optional.of(CalculationRequest(adjustments = objectToJson(noSentenceAdjustment, jacksonObjectMapper().findAndRegisterModules()))))
    val analyzedAdjustments = underTest.getAnalyzedAdjustments(123)
    assertThat(analyzedAdjustments).isNotNull
    assertThat(analyzedAdjustments.bookingAdjustments).hasSize(1)
    assertThat(analyzedAdjustments.sentenceAdjustments).hasSize(1)
    assertThat(analyzedAdjustments.bookingAdjustments[0].analysisResult).isEqualTo(AdjustmentAnalysisResult.SAME)
    assertThat(analyzedAdjustments.sentenceAdjustments[0].analysisResult).isEqualTo(AdjustmentAnalysisResult.NEW)
  }
}
