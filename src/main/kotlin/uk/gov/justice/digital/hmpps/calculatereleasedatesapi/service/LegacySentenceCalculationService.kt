package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.LegacySentenceCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.PrisonerModifiedReason
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.migration.LegacySentenceCalculationDto
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.migration.LegacySentenceCalculationMigrationRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.migration.LegacySentenceCalculationMigrationResponse
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.migration.LegacySentenceCalculationToCrdsMappingResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.ApprovedDatesSubmissionRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationRequestRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.LegacySentenceCalculationRepository
import java.util.UUID
import kotlin.jvm.optionals.getOrNull

@Service
class LegacySentenceCalculationService(
  private val calculationRequestRepository: CalculationRequestRepository,
  private val legacySentenceCalculationRepository: LegacySentenceCalculationRepository,
  private val approvedDatesSubmissionRepository: ApprovedDatesSubmissionRepository,
) {

  @Transactional
  fun migrate(prisonerId: String, request: LegacySentenceCalculationMigrationRequest): LegacySentenceCalculationMigrationResponse {
    legacySentenceCalculationRepository.deleteAllByPrisonerId(prisonerId)
    val mappingResults = request.sentenceCalculations.map { legacySentenceCalculation ->
      val maybeAnExistingCalculationRequest = findCalculationRequestFromNomisComment(legacySentenceCalculation)
      // TODO if this is for a prisoner with indeterminate sentences and the latest calculation is not from CRDS then create a new calculation request from the NOMIS calculation
      val entity = LegacySentenceCalculation.fromDto(legacySentenceCalculation, maybeAnExistingCalculationRequest)
      legacySentenceCalculationRepository.saveAndFlush(entity)

      maybeAnExistingCalculationRequest?.let { existingCalculationRequest ->
        if (existingCalculationRequest.prisonerId != legacySentenceCalculation.prisonerId) {
          // this will be from a booking move or prisoner merge that occurred before CRDS supported it
          logger.info("Moving existing calculation ${existingCalculationRequest.id()} from prisoner ${existingCalculationRequest.prisonerId} to prisoner ${legacySentenceCalculation.prisonerId}")
          calculationRequestRepository.setPrisonerIdForCalculationRequest(
            calculationRequestId = existingCalculationRequest.id(),
            fromPrisoner = existingCalculationRequest.prisonerId,
            toPrisoner = legacySentenceCalculation.prisonerId,
            reason = PrisonerModifiedReason.MIGRATION,
          )
          approvedDatesSubmissionRepository.setPrisonerIdForCalculationRequest(
            calculationRequestId = existingCalculationRequest.id(),
            fromPrisoner = existingCalculationRequest.prisonerId,
            toPrisoner = legacySentenceCalculation.prisonerId,
            reason = PrisonerModifiedReason.MIGRATION,
          )
        }
      }
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
    private val logger = LoggerFactory.getLogger(LegacySentenceCalculationService::class.java)
  }
}
