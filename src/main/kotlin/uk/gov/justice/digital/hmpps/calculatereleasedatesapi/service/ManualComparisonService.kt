package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.persistence.EntityNotFoundException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.Comparison
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ComparisonOverview
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ComparisonSummary
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ManualComparisonInput
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.ComparisonPersonRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.ComparisonRepository

@Service
class ManualComparisonService(
  private var comparisonRepository: ComparisonRepository,
  private var serviceUserService: ServiceUserService,
  private val comparisonPersonRepository: ComparisonPersonRepository,
  private var bulkComparisonService: BulkComparisonService,
  private val objectMapper: ObjectMapper,
) {

  fun create(manualComparison: ManualComparisonInput): Comparison {
    val comparisonToCreate = transform(objectMapper.valueToTree(manualComparison), serviceUserService.getUsername())
    val initialComparisonCreated = comparisonRepository.save(
      comparisonToCreate,
    )
    bulkComparisonService.processManualComparison(initialComparisonCreated, manualComparison.prisonerIds)

    return initialComparisonCreated
  }

  fun listManual(): List<ComparisonSummary> {
    return comparisonRepository.findAllByManualInput(true).map { transform(it) }
  }

  fun getCountOfPersonsInComparisonByComparisonReference(shortReference: String): Long {
    return comparisonRepository.findByManualInputAndComparisonShortReference(true, shortReference)?.let {
      comparisonPersonRepository.countByComparisonId(it.id)
    } ?: 0
  }

  fun getComparisonByComparisonReference(comparisonReference: String): ComparisonOverview {
    val comparison = comparisonRepository.findByManualInputAndComparisonShortReference(true, comparisonReference) ?: throw EntityNotFoundException("No comparison results exist for comparisonReference $comparisonReference ")
    val mismatches = comparisonPersonRepository.findByComparisonIdIs(comparison.id)
    return transform(comparison, mismatches)
  }

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}
