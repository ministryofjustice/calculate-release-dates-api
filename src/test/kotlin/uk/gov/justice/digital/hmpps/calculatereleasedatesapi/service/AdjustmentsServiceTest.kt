package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.TestUtil
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.adjustmentsapi.model.AdjustmentDto
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.adjustmentsapi.model.AdjustmentDto.AdjustmentType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AdjustmentAnalysisResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationRequestRepository
import java.time.LocalDate
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class AdjustmentsServiceTest {

  @Mock
  lateinit var prisonService: PrisonService

  @Mock
  lateinit var calculationRequestRepository: CalculationRequestRepository

  @Mock
  lateinit var sourceDataMapper: SourceDataMapper

  @Mock
  lateinit var adjustmentsApiClient: AdjustmentsApiClient

  @InjectMocks
  lateinit var underTest: AdjustmentsService

  @Test
  fun `All adjustments are new`() {
    val adjustmentDtos = listOf(AdjustmentDto("123", AdjustmentType.ADDITIONAL_DAYS_AWARDED, UUID.randomUUID(), 1L, null, LocalDate.MIN, 3))
    whenever(adjustmentsApiClient.getAdjustmentsByPerson(any(), any(), any())).thenReturn(adjustmentDtos)
    whenever(calculationRequestRepository.findFirstByPrisonerIdAndCalculationStatusOrderByCalculatedAtDesc(any(), anyString())).thenReturn(Optional.empty())
    val analysedAdjustments = underTest.getAnalysedAdjustments("123")
    assertThat(analysedAdjustments).isNotNull
    assertThat(analysedAdjustments).hasSize(1)
    assertThat(analysedAdjustments[0].analysisResult).isEqualTo(AdjustmentAnalysisResult.NEW)
  }

  @Test
  fun `All adjustments are same`() {
    val adjustmentDtos = listOf(AdjustmentDto("123", AdjustmentType.ADDITIONAL_DAYS_AWARDED, UUID.randomUUID(), 1L, null, LocalDate.MIN, 3))
    val calculationRequest = CalculationRequest(id = 1L, adjustments = objectToJson(adjustmentDtos, TestUtil.objectMapper()))
    whenever(adjustmentsApiClient.getAdjustmentsByPerson(any(), any(), any())).thenReturn(adjustmentDtos)
    whenever(calculationRequestRepository.findFirstByPrisonerIdAndCalculationStatusOrderByCalculatedAtDesc(any(), anyString())).thenReturn(Optional.of(calculationRequest))
    whenever(sourceDataMapper.mapAdjustments(calculationRequest)).thenReturn(adjustmentDtos)
    val analysedAdjustments = underTest.getAnalysedAdjustments("123")
    assertThat(analysedAdjustments).isNotNull
    assertThat(analysedAdjustments).hasSize(1)
    assertThat(analysedAdjustments[0].analysisResult).isEqualTo(AdjustmentAnalysisResult.SAME)
  }

  @Test
  fun `There is a new adjustment`() {
    val ada = AdjustmentDto("123", AdjustmentType.ADDITIONAL_DAYS_AWARDED, UUID.randomUUID(), 1L, null, LocalDate.MIN, 3)
    val taggedBail = AdjustmentDto("123", AdjustmentType.TAGGED_BAIL, UUID.randomUUID(), 1L, LocalDate.MIN.plusDays(5), LocalDate.MIN, 5)

    val latestAdjustments = listOf(ada, taggedBail)
    val previousAdjustments = listOf(ada)
    val calculationRequest = CalculationRequest(id = 1L, adjustments = objectToJson(previousAdjustments, TestUtil.objectMapper()))
    whenever(adjustmentsApiClient.getAdjustmentsByPerson(any(), any(), any())).thenReturn(latestAdjustments)
    whenever(calculationRequestRepository.findFirstByPrisonerIdAndCalculationStatusOrderByCalculatedAtDesc(any(), anyString())).thenReturn(Optional.of(calculationRequest))
    whenever(sourceDataMapper.mapAdjustments(calculationRequest)).thenReturn(previousAdjustments)
    val analysedAdjustments = underTest.getAnalysedAdjustments("123")
    assertThat(analysedAdjustments).isNotNull
    assertThat(analysedAdjustments).hasSize(2)
    assertThat(analysedAdjustments.find { it.adjustment.adjustmentType == AdjustmentType.ADDITIONAL_DAYS_AWARDED }!!.analysisResult).isEqualTo(AdjustmentAnalysisResult.SAME)
    assertThat(analysedAdjustments.find { it.adjustment.adjustmentType == AdjustmentType.TAGGED_BAIL }!!.analysisResult).isEqualTo(AdjustmentAnalysisResult.NEW)
  }
}
