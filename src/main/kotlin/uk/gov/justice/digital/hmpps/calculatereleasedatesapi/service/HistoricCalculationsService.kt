package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationStatus.CONFIRMED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationSource
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationViewConfiguration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.HistoricCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationRequestRepository

@Service
class HistoricCalculationsService(
  private val prisonApiClient: PrisonApiClient,
  private val calculationRequestRepository: CalculationRequestRepository,
) {

  fun getHistoricCalculationsForPrisoner(prisonerId: String): List<HistoricCalculation> {
    val calculations = calculationRequestRepository.findAllByPrisonerIdAndCalculationStatus(prisonerId, CONFIRMED.name)
    val nomisCalculations = prisonApiClient.getCalculationsForAPrisonerId(prisonerId)
    val historicCalculations = nomisCalculations.map { nomisCalculation ->
      var source = CalculationSource.NOMIS
      var calculationViewData: CalculationViewConfiguration? = null
      var calculationType: CalculationType? = null
      var calculationRequestId: Long? = null
      var calculationReason = nomisCalculation.calculationReason
      for (calculation in calculations) {
        val nomisComment = nomisCalculation.commentText
        if (nomisComment != null && nomisCalculation.commentText.contains(calculation.calculationReference.toString())) {
          source = CalculationSource.CRDS
          calculationType = calculation.calculationType
          calculationViewData = CalculationViewConfiguration(calculation.calculationReference.toString(), calculation.id)
          calculation.calculationReference
          calculationRequestId = calculation.id
          if (calculation.reasonForCalculation != null) {
            calculationReason = calculation.reasonForCalculation.displayName
          }
        }
      }
      HistoricCalculation(prisonerId, nomisCalculation.calculationDate, source, calculationViewData, nomisCalculation.commentText, calculationType, nomisCalculation.agencyDescription, calculationRequestId, calculationReason)
    }
    return historicCalculations
  }
}
