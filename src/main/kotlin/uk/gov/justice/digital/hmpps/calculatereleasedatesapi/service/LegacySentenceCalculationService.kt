package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import jakarta.transaction.Transactional
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.LegacySentenceCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.migration.LegacySentenceCalculationDto
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.migration.LegacySentenceCalculationMigrationRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.migration.LegacySentenceCalculationMigrationResponse
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.migration.LegacySentenceCalculationToCrdsMappingResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationRequestRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.LegacySentenceCalculationRepository
import java.util.UUID
import kotlin.jvm.optionals.getOrNull

@Service
class LegacySentenceCalculationService(private val calculationRequestRepository: CalculationRequestRepository, private val legacySentenceCalculationRepository: LegacySentenceCalculationRepository) {

  @Transactional
  fun migrate(prisonerId: String, request: LegacySentenceCalculationMigrationRequest): LegacySentenceCalculationMigrationResponse {
    legacySentenceCalculationRepository.deleteAllByPrisonerId(prisonerId)
    val mappingResults = request.sentenceCalculations.map { legacySentenceCalculation ->
      val maybeAnExistingCalculationRequest = findCalculationRequestFromNomisComment(legacySentenceCalculation)
      // TODO if the prisoner id or booking id don't match then this should be a prisoner merge / booking move that we have missed so update the calculation request and approved dates submissions to match the new prisoner id / booking id.
      // TODO if this is for a prisoner with indeterminate sentences and the latest calculation is not from CRDS then create a new calculation request from the NOMIS calculation
      val entity = LegacySentenceCalculation.fromDto(legacySentenceCalculation, maybeAnExistingCalculationRequest)
      legacySentenceCalculationRepository.saveAndFlush(entity)
      LegacySentenceCalculationToCrdsMappingResult(legacySentenceCalculation.nomisCalculationId, maybeAnExistingCalculationRequest?.id)
    }
    return LegacySentenceCalculationMigrationResponse(mappingResults)
  }

  private fun findCalculationRequestFromNomisComment(legacySentenceCalculation: LegacySentenceCalculationDto): CalculationRequest? {
    val commentText = legacySentenceCalculation.commentText
    if (commentText.isNullOrBlank()) {
      return null
    }
    val uuids = uuidRegex.findAll(commentText).mapNotNull { runCatching { UUID.fromString(it.value) }.getOrNull() }.toList()
    require(uuids.size <= 1) { "Found ${uuids.size} UUIDs in the comment for NOMIS calculation ${legacySentenceCalculation.nomisCalculationId}" }
    return uuids.firstOrNull()?.let {
      calculationRequestRepository.findByCalculationReference(it).getOrNull()
    }
  }

  companion object {
    private val uuidRegex = Regex(
      "\\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\b",
    )
  }
}
