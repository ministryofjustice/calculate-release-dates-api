package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.TestUtil
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AdjustmentAnalysisResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BookingAdjustment
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BookingAdjustmentType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAdjustment
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAdjustmentType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi.BookingAndSentenceAdjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationRequestRepository
import java.time.LocalDate
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class AdjustmentsServiceTest {

  @Mock
  lateinit var prisonService: PrisonService

  @Mock
  lateinit var calculationRequestRepository: CalculationRequestRepository

  @Mock
  lateinit var sourceDataMapper: SourceDataMapper

  @InjectMocks
  lateinit var underTest: AdjustmentsService

  @Test
  fun `All adjustments are new`() {
    val bookingAndOffenceAdjustments = BookingAndSentenceAdjustments(listOf(BookingAdjustment(true, LocalDate.MIN, null, 3, BookingAdjustmentType.ADDITIONAL_DAYS_AWARDED)), emptyList())
    whenever(prisonService.getBookingAndSentenceAdjustments(anyLong(), anyBoolean())).thenReturn(bookingAndOffenceAdjustments)
    whenever(calculationRequestRepository.findFirstByBookingIdAndCalculationStatusOrderByCalculatedAtDesc(anyLong(), anyString())).thenReturn(Optional.empty())
    val analyzedAdjustments = underTest.getAnalyzedAdjustments(123)
    assertThat(analyzedAdjustments).isNotNull
    assertThat(analyzedAdjustments.bookingAdjustments).hasSize(1)
    assertThat(analyzedAdjustments.bookingAdjustments[0].analysisResult).isEqualTo(AdjustmentAnalysisResult.NEW)
  }

  @Test
  fun `All adjustments are same`() {
    val bookingAndOffenceAdjustments = BookingAndSentenceAdjustments(listOf(BookingAdjustment(true, LocalDate.MIN, null, 3, BookingAdjustmentType.ADDITIONAL_DAYS_AWARDED)), emptyList())
    val calculationRequest = CalculationRequest(id = 1L, adjustments = objectToJson(bookingAndOffenceAdjustments, TestUtil.objectMapper()))
    whenever(prisonService.getBookingAndSentenceAdjustments(anyLong(), anyBoolean())).thenReturn(bookingAndOffenceAdjustments)
    whenever(calculationRequestRepository.findFirstByBookingIdAndCalculationStatusOrderByCalculatedAtDesc(anyLong(), anyString())).thenReturn(Optional.of(calculationRequest))
    whenever(sourceDataMapper.mapBookingAndSentenceAdjustments(calculationRequest)).thenReturn(bookingAndOffenceAdjustments)
    val analyzedAdjustments = underTest.getAnalyzedAdjustments(123)
    assertThat(analyzedAdjustments).isNotNull
    assertThat(analyzedAdjustments.bookingAdjustments).hasSize(1)
    assertThat(analyzedAdjustments.bookingAdjustments[0].analysisResult).isEqualTo(AdjustmentAnalysisResult.SAME)
  }

  @Test
  fun `There is a new adjustment`() {
    val ada = BookingAdjustment(true, LocalDate.MIN, null, 3, BookingAdjustmentType.ADDITIONAL_DAYS_AWARDED)
    val taggedBail = SentenceAdjustment(1, true, LocalDate.MIN, null, 5, SentenceAdjustmentType.TAGGED_BAIL)
    val bookingAndOffenceAdjustments = BookingAndSentenceAdjustments(listOf(ada), listOf(taggedBail))
    val noSentenceAdjustments = BookingAndSentenceAdjustments(listOf(ada), emptyList())
    val calculationRequest = CalculationRequest(id = 1L, adjustments = objectToJson(noSentenceAdjustments, TestUtil.objectMapper()))
    whenever(prisonService.getBookingAndSentenceAdjustments(anyLong(), anyBoolean())).thenReturn(bookingAndOffenceAdjustments)
    whenever(calculationRequestRepository.findFirstByBookingIdAndCalculationStatusOrderByCalculatedAtDesc(anyLong(), anyString())).thenReturn(Optional.of(calculationRequest))
    whenever(sourceDataMapper.mapBookingAndSentenceAdjustments(calculationRequest)).thenReturn(noSentenceAdjustments)
    val analyzedAdjustments = underTest.getAnalyzedAdjustments(123)
    assertThat(analyzedAdjustments).isNotNull
    assertThat(analyzedAdjustments.bookingAdjustments).hasSize(1)
    assertThat(analyzedAdjustments.sentenceAdjustments).hasSize(1)
    assertThat(analyzedAdjustments.bookingAdjustments[0].analysisResult).isEqualTo(AdjustmentAnalysisResult.SAME)
    assertThat(analyzedAdjustments.sentenceAdjustments[0].analysisResult).isEqualTo(AdjustmentAnalysisResult.NEW)
  }
}
