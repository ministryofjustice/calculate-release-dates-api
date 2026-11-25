package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import com.fasterxml.jackson.databind.ObjectMapper
import io.hypersistence.utils.hibernate.type.json.internal.JacksonUtil
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.boot.info.BuildProperties
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationOutcome
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.LegacySentenceCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationStatus
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.PrisonerModifiedReason
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.migration.LegacySentenceCalculationDto
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.migration.LegacySentenceCalculationMigrationRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.migration.LegacySentenceCalculationMigrationResponse
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.migration.LegacySentenceCalculationToCrdsMappingResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.ApprovedDatesSubmissionRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationOutcomeRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationReasonRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationRequestRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.LegacySentenceCalculationRepository
import java.time.LocalDate
import java.util.UUID
import kotlin.jvm.optionals.getOrNull

@Service
class LegacySentenceCalculationService(
  private val calculationRequestRepository: CalculationRequestRepository,
  private val legacySentenceCalculationRepository: LegacySentenceCalculationRepository,
  private val approvedDatesSubmissionRepository: ApprovedDatesSubmissionRepository,
  private val buildProperties: BuildProperties,
  private val calculationReasonRepository: CalculationReasonRepository,
  private val calculationOutcomeRepository: CalculationOutcomeRepository,
  private val objectMapper: ObjectMapper,
) {

  @Transactional
  fun migrate(prisonerId: String, request: LegacySentenceCalculationMigrationRequest): LegacySentenceCalculationMigrationResponse {
    legacySentenceCalculationRepository.deleteAllByPrisonerId(prisonerId)
    val mappingResults = request.sentenceCalculations.sortedByDescending { it.calculationDate }.mapIndexed { index, legacySentenceCalculation ->
      val isTheLatestCalc = index == 0
      linkAndSave(legacySentenceCalculation, canCreateNew = isTheLatestCalc)
    }
    return LegacySentenceCalculationMigrationResponse(mappingResults)
  }

  @Transactional
  fun syncNew(legacySentenceCalculation: LegacySentenceCalculationDto): LegacySentenceCalculationToCrdsMappingResult = linkAndSave(legacySentenceCalculation, canCreateNew = false)

  @Transactional
  fun syncUpdated(legacySentenceCalculation: LegacySentenceCalculationDto): LegacySentenceCalculationToCrdsMappingResult {
    legacySentenceCalculationRepository.deleteById(legacySentenceCalculation.nomisCalculationId)
    return linkAndSave(legacySentenceCalculation, canCreateNew = false)
  }

  @Transactional
  fun syncDeleted(nomisCalculationId: Long) {
    legacySentenceCalculationRepository.deleteById(nomisCalculationId)
  }

  @Transactional
  fun getAllForPrisoner(prisonerId: String): List<LegacySentenceCalculationDto> = legacySentenceCalculationRepository.getByPrisonerId(prisonerId).map { it.toDto() }

  private fun linkAndSave(legacySentenceCalculation: LegacySentenceCalculationDto, canCreateNew: Boolean): LegacySentenceCalculationToCrdsMappingResult {
    var maybeAnExistingCalculationRequest = findCalculationRequestFromNomisComment(legacySentenceCalculation)
    if (canCreateNew && maybeAnExistingCalculationRequest == null) {
      maybeAnExistingCalculationRequest = createCalculationRequestFromLegacySentenceCalculation(legacySentenceCalculation)
    }
    val entity = LegacySentenceCalculation.fromDto(legacySentenceCalculation, maybeAnExistingCalculationRequest)
    legacySentenceCalculationRepository.saveAndFlush(entity)
    maybeAnExistingCalculationRequest?.let { existingCalculationRequest ->
      moveCalculationRequestToNewPrisonerIfRequired(existingCalculationRequest, legacySentenceCalculation)
    }
    return LegacySentenceCalculationToCrdsMappingResult(legacySentenceCalculation.nomisCalculationId, maybeAnExistingCalculationRequest?.id)
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

  private fun moveCalculationRequestToNewPrisonerIfRequired(existingCalculationRequest: CalculationRequest, legacySentenceCalculation: LegacySentenceCalculationDto) {
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

  private fun createCalculationRequestFromLegacySentenceCalculation(legacySentenceCalculation: LegacySentenceCalculationDto): CalculationRequest {
    val calculationReason = calculationReasonRepository.findAllByIsActiveTrueOrderByDisplayRankAsc().firstOrNull { it.nomisReason == legacySentenceCalculation.calculationReasonCode }
    val calculationRequest = calculationRequestRepository.saveAndFlush(
      CalculationRequest(
        prisonerId = legacySentenceCalculation.prisonerId,
        bookingId = legacySentenceCalculation.bookingId,
        calculationStatus = CalculationStatus.CONFIRMED.name,
        calculatedAt = legacySentenceCalculation.calculationDate,
        calculatedByUsername = legacySentenceCalculation.nomisCreatedBy,
        prisonerLocation = null,
        inputData = JacksonUtil.toJsonNode("{}"),
        calculationType = CalculationType.ENTERED_IN_NOMIS,
        reasonForCalculation = calculationReason,
        version = buildProperties.version ?: "",
      ),
    )

    val calculationOutcomes = listOfNotNull(
      createOutcome(calculationRequest, ReleaseDateType.HDCED, legacySentenceCalculation.hdcedCalculatedDate, legacySentenceCalculation.hdcedOverriddenDate),
      createOutcome(calculationRequest, ReleaseDateType.HDCAD, legacySentenceCalculation.hdcadCalculatedDate, legacySentenceCalculation.hdcadOverriddenDate),
      createOutcome(calculationRequest, ReleaseDateType.ETD, legacySentenceCalculation.etdCalculatedDate, legacySentenceCalculation.etdOverriddenDate),
      createOutcome(calculationRequest, ReleaseDateType.MTD, legacySentenceCalculation.mtdCalculatedDate, legacySentenceCalculation.mtdOverriddenDate),
      createOutcome(calculationRequest, ReleaseDateType.LTD, legacySentenceCalculation.ltdCalculatedDate, legacySentenceCalculation.ltdOverriddenDate),
      createOutcome(calculationRequest, ReleaseDateType.ARD, legacySentenceCalculation.ardCalculatedDate, legacySentenceCalculation.ardOverriddenDate),
      createOutcome(calculationRequest, ReleaseDateType.CRD, legacySentenceCalculation.crdCalculatedDate, legacySentenceCalculation.crdOverriddenDate),
      createOutcome(calculationRequest, ReleaseDateType.PED, legacySentenceCalculation.pedCalculatedDate, legacySentenceCalculation.pedOverriddenDate),
      createOutcome(calculationRequest, ReleaseDateType.APD, legacySentenceCalculation.apdCalculatedDate, legacySentenceCalculation.apdOverriddenDate),
      createOutcome(calculationRequest, ReleaseDateType.NPD, legacySentenceCalculation.npdCalculatedDate, legacySentenceCalculation.npdOverriddenDate),
      createOutcome(calculationRequest, ReleaseDateType.LED, legacySentenceCalculation.ledCalculatedDate, legacySentenceCalculation.ledOverriddenDate),
      createOutcome(calculationRequest, ReleaseDateType.SED, legacySentenceCalculation.sedCalculatedDate, legacySentenceCalculation.sedOverriddenDate),
      createOutcome(calculationRequest, ReleaseDateType.PRRD, legacySentenceCalculation.prrdCalculatedDate, legacySentenceCalculation.prrdOverriddenDate),
      createOutcome(calculationRequest, ReleaseDateType.Tariff, legacySentenceCalculation.tariffCalculatedDate, legacySentenceCalculation.tariffOverriddenDate),
      createOutcome(calculationRequest, ReleaseDateType.DPRRD, legacySentenceCalculation.dprrdCalculatedDate, legacySentenceCalculation.dprrdOverriddenDate),
      createOutcome(calculationRequest, ReleaseDateType.ERSED, null, legacySentenceCalculation.ersedOverriddenDate),
      createOutcome(calculationRequest, ReleaseDateType.TERSED, null, legacySentenceCalculation.tersedOverriddenDate),
      createOutcome(calculationRequest, ReleaseDateType.ROTL, null, legacySentenceCalculation.rotlOverriddenDate),
      createOutcome(calculationRequest, ReleaseDateType.TUSED, legacySentenceCalculation.tusedCalculatedDate, legacySentenceCalculation.tusedOverriddenDate),
    )
    if (calculationOutcomes.isNotEmpty()) {
      calculationOutcomes.onEach { calculationOutcomeRepository.saveAndFlush(it) }
    } else {
      calculationOutcomeRepository.saveAndFlush(CalculationOutcome(calculationRequestId = calculationRequest.id(), calculationDateType = ReleaseDateType.None.name, outcomeDate = null))
    }
    return calculationRequest
  }

  private fun createOutcome(calculationRequest: CalculationRequest, type: ReleaseDateType, calculatedDate: LocalDate?, overriddenDate: LocalDate?): CalculationOutcome? = when {
    overriddenDate != null -> transform(calculationRequest, type, overriddenDate)
    calculatedDate != null -> transform(calculationRequest, type, calculatedDate)
    else -> null
  }

  companion object {
    private val uuidRegex = Regex(
      "\\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\b",
    )
    private val logger = LoggerFactory.getLogger(LegacySentenceCalculationService::class.java)
  }
}
