package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import jakarta.persistence.EntityNotFoundException
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationStatus.RECORD_A_RECALL
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculatedReleaseDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationRequestModel
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationReasonRepository

@Service
class RecallService(
  private val calculationTransactionalService: CalculationTransactionalService,
  private val calculationReasonRepository: CalculationReasonRepository,
) {

  fun calculateForRecordARecall(prisonerId: String): CalculatedReleaseDates {
    val recallReason = calculationReasonRepository.findByDisplayName(RECALL_REASON)
      ?: throw EntityNotFoundException()

    val calculationRequestModel = CalculationRequestModel(
      calculationReasonId = recallReason.id,
      otherReasonDescription = RECALL_REASON,
      calculationUserInputs = null,
    )
    return calculationTransactionalService.calculate(
      prisonerId,
      calculationRequestModel,
      InactiveDataOptions.overrideToIncludeInactiveData(),
      calculationType = RECORD_A_RECALL,
    )
  }

  companion object {
    const val RECALL_REASON = "Requested by record-a-recall service"
  }
}
