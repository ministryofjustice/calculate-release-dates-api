package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.stereotype.Service
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
      for (calculation in calculations) {
        if (nomisCalculation.commentText.contains(calculation.calculationReference.toString())) {
          source = CalculationSource.CRDS
          calculationViewData = CalculationViewConfiguration(calculation.calculationReference.toString(), calculation.id)
        }
      }
      HistoricCalculation(nomisCalculation.calculationDate, source, calculationViewData, nomisCalculation.commentText)
    }
    return historicCalculations
  }
}
