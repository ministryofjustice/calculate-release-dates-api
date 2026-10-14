package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import jakarta.transaction.Transactional
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationOutcomeRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationRequestRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationRequestSentenceOutcomeRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationRequestSentenceRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.OperativeSentenceEnvelopeRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.TrancheOutcomeRepository

@Service
class DeleteCalculationService(
  private val calculationRequestRepository: CalculationRequestRepository,
  private val calculationOutcomeRepository: CalculationOutcomeRepository,
  private val calculationRequestSentenceRepository: CalculationRequestSentenceRepository,
  private val calculationRequestSentenceOutcomeRepository: CalculationRequestSentenceOutcomeRepository,
  private val operativeSentenceEnvelopeRepository: OperativeSentenceEnvelopeRepository,
  private val trancheOutcomeRepository: TrancheOutcomeRepository,
) {

  /**
   * REQUIRES_NEW so that the batch clean up of old preliminary calculations only fails for a single calculation request.
   *
   * Deletes from the following tables with constrains on calculation request
   * calculation_outcome
   * calculation_request_sentence
   * calculation_request_user_input
   * operative_sentence_envelope
   * tranche_outcome
   * calculation_request
   *
   * The following are not applicable to PRELIMINARY calculations so are skipped
   * approved_dates_submission
   * calculation_outcome_historic_sled_override
   * comparison_person
   * calculation_request_second_check
   * calculation_request_manual_reason
   */
  @Transactional(Transactional.TxType.REQUIRES_NEW)
  fun deletePreliminary(calculationRequestId: Long) {
    calculationOutcomeRepository.deleteByCalculationRequestId(calculationRequestId)
    calculationRequestSentenceOutcomeRepository.deleteByCalculationRequestId(calculationRequestId)
    calculationRequestSentenceRepository.deleteByCalculationRequestId(calculationRequestId)
    operativeSentenceEnvelopeRepository.deleteByCalculationRequestId(calculationRequestId)
    trancheOutcomeRepository.deleteByCalculationRequestId(calculationRequestId)
    calculationRequestRepository.deleteUserInput(calculationRequestId)
    calculationRequestRepository.deleteByCalculationRequestId(calculationRequestId)
  }
}
