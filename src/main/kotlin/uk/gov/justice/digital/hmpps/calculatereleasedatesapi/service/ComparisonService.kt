package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.persistence.EntityNotFoundException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.Comparison
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.CrdWebException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ManualComparisonInput
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.ComparisonInput
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.ComparisonPersonRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.ComparisonRepository

@Service
class ComparisonService(
  private var comparisonRepository: ComparisonRepository,
  private var prisonService: PrisonService,
  private var serviceUserService: ServiceUserService,
  private val comparisonPersonRepository: ComparisonPersonRepository,
  private var bulkComparisonService: BulkComparisonService,
  private val objectMapper: ObjectMapper,
) {

  fun create(comparisonInput: ComparisonInput): Comparison {
    val comparisonToCreate = transform(comparisonInput, serviceUserService.getUsername())
    log.info("Creating new comparison $comparisonToCreate")
    val initialComparisonCreated = comparisonRepository.save(
      comparisonToCreate,
    )
    if (!comparisonToCreate.manualInput) {
      this.bulkComparisonService.processPrisonComparison(initialComparisonCreated)
    }
    return initialComparisonCreated
  }

  fun create(manualComparison: ManualComparisonInput): Comparison {
    val comparisonToCreate = transform(objectMapper.valueToTree(manualComparison), serviceUserService.getUsername())
    val initialComparisonCreated = comparisonRepository.save(
      comparisonToCreate,
    )
    bulkComparisonService.processManualComparison(initialComparisonCreated, manualComparison.prisonerIds)

    return initialComparisonCreated
  }

  fun listManual(): List<Comparison> {
    return comparisonRepository.findAllByManualInput(true)
  }

  fun listComparisons(): List<Comparison> {
    return comparisonRepository.findAllByManualInputAndPrisonIsIn(
      false,
      prisonService.getCurrentUserPrisonsList(),
    )
  }

  fun getCountOfPersonsInComparisonByComparisonReference(shortReference: String): Long {
    val comparison = comparisonRepository.findByManualInputAndComparisonShortReference(false, shortReference)

    if (comparison?.prison != null && prisonService.getCurrentUserPrisonsList().contains(comparison.prison)) {
      return comparisonPersonRepository.countByComparisonId(comparison.id)
    }
    return 0
  }

  fun getComparisonByComparisonReference(comparisonReference: String): Comparison {
    val comparison = comparisonRepository.findByManualInputAndComparisonShortReference(false, comparisonReference) ?: throw EntityNotFoundException("No comparison results exist for comparisonReference $comparisonReference ")

    if (comparison.prison != null && prisonService.getCurrentUserPrisonsList().contains(comparison.prison)) {
      return comparison
    }
    throw CrdWebException("Forbidden", HttpStatus.FORBIDDEN, 403.toString())
  }

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}
