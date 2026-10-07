package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import jakarta.persistence.EntityNotFoundException
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationOutcomeRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationRequestRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationRequestSentenceOutcomeRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationRequestSentenceRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.OperativeSentenceEnvelopeRepository
import kotlin.jvm.optionals.getOrElse

@Service
class DeleteCalculationService(
  private val calculationRequestRepository: CalculationRequestRepository,
  private val calculationOutcomeRepository: CalculationOutcomeRepository,
  private val calculationRequestSentenceRepository: CalculationRequestSentenceRepository,
  private val calculationRequestSentenceOutcomeRepository: CalculationRequestSentenceOutcomeRepository,
  private val operativeSentenceEnvelopeRepository: OperativeSentenceEnvelopeRepository,
) {

  @Transactional
  fun delete(calculationRequestId: Long) {
    val calculationRequest = calculationRequestRepository.findById(calculationRequestId).getOrElse { throw EntityNotFoundException("Tried to delete a calculation request that doesn't exist: $calculationRequestId") }

    // delete entities unlinked in JPA but with constraints in the database
    calculationOutcomeRepository.deleteByCalculationRequestId(calculationRequestId)
    calculationRequestSentenceRepository.findByCalculationRequestId(calculationRequestId).forEach { calculationRequestSentence ->
      calculationRequestSentenceOutcomeRepository.deleteByCalculationRequestSentenceId(calculationRequestSentence.id!!)
      calculationRequestSentenceRepository.delete(calculationRequestSentence)
    }
    operativeSentenceEnvelopeRepository.deleteByCalculationRequestId(calculationRequestId)

    // finally delete calculation request that will cascade to certain entities with required mappings as well
    calculationRequestRepository.delete(calculationRequest)
  }
}
