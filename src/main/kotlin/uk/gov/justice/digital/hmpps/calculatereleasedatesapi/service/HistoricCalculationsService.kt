package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationStatus.CONFIRMED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationSource
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationViewConfiguration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.HistoricCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationRequestRepository

@Service
class HistoricCalculationsService(
  private val prisonService: PrisonService,
  private val calculationRequestRepository: CalculationRequestRepository,
) {

  @Transactional(readOnly = true)
  fun getHistoricCalculationsForPrisoner(prisonerId: String): List<HistoricCalculation> {
    val calculations = calculationRequestRepository.findAllByPrisonerIdAndCalculationStatus(prisonerId, CONFIRMED.name)
    val nomisCalculations = prisonService.getCalculationsForAPrisonerId(prisonerId)
    val agencyIdToDescriptionMap = prisonService.getAgenciesByType("INST").associateBy { it.agencyId }
    val historicCalculations = nomisCalculations.map { nomisCalculation ->
      var source = CalculationSource.NOMIS
      var calculationViewData: CalculationViewConfiguration? = null
      var calculationType: CalculationType? = null
      var calculationRequestId: Long? = null
      var calculationReason: String? = nomisCalculation.calculationReason
      var establishment: String? = null
      val nomisComment = nomisCalculation.commentText
      calculations.firstOrNull {
        nomisComment != null && nomisCalculation.commentText.contains(it.calculationReference.toString())
      }?.let {
        establishment = agencyIdToDescriptionMap[it.prisonerLocation]?.description
        source = CalculationSource.CRDS
        calculationType = it.calculationType
        calculationViewData = CalculationViewConfiguration(it.calculationReference.toString(), it.id)
        calculationRequestId = it.id
        calculationReason = it.reasonForCalculation?.displayName
      }

      HistoricCalculation(prisonerId, nomisCalculation.calculationDate, source, calculationViewData, nomisCalculation.commentText, calculationType, establishment, calculationRequestId, calculationReason, nomisCalculation.offenderSentCalculationId)
    }
    return historicCalculations
  }
}
