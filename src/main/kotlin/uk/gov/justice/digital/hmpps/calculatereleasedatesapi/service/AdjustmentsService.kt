package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.adjustmentsapi.model.AdjustmentDto
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AdjustmentAnalysisResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AnalysedAdjustment
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationRequestRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.apiclients.AdjustmentsApiClient

@Service
class AdjustmentsService(
  private val calculationRequestRepository: CalculationRequestRepository,
  private val sourceDataMapper: SourceDataMapper,
  private val adjustmentsApiClient: AdjustmentsApiClient,
) {

  fun getAnalysedAdjustments(prisonerId: String): List<AnalysedAdjustment> {
    val adjustments = adjustmentsApiClient.getAdjustmentsByPerson(prisonerId, listOf(AdjustmentDto.Status.ACTIVE))

    return calculationRequestRepository.findFirstByPrisonerIdAndCalculationStatusOrderByCalculatedAtDesc(prisonerId).map { calculationRequest ->
      if (calculationRequest.adjustments == null) {
        adjustments.map { AdjustmentAnalysisResult.NEW.adjustment(it) }
      } else {
        val lastAdjustments = sourceDataMapper.mapAdjustments(calculationRequest)
        adjustments.map {
          if (lastAdjustments.any { inner -> inner.adjustmentType == it.adjustmentType && inner.days == it.days }) {
            AdjustmentAnalysisResult.SAME.adjustment(it)
          } else {
            AdjustmentAnalysisResult.NEW.adjustment(it)
          }
        }
      }
    }.orElse(adjustments.map { AdjustmentAnalysisResult.NEW.adjustment(it) })
  }
}
