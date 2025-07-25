package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.persistence.EntityNotFoundException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.Comparison
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.manualComparisonTypes
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ComparisonDiscrepancySummary
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ComparisonOverview
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ComparisonPersonOverview
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ComparisonSummary
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CreateComparisonDiscrepancyRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ManualComparisonInput
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ReleaseDateCalculationBreakdown
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceAndOffenceWithReleaseArrangements
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.ComparisonPersonDiscrepancyRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.ComparisonPersonRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.ComparisonRepository
import java.time.LocalDate

@Service
class ManualComparisonService(
  private var comparisonRepository: ComparisonRepository,
  private var serviceUserService: ServiceUserService,
  private val comparisonPersonRepository: ComparisonPersonRepository,
  private val comparisonPersonDiscrepancyRepository: ComparisonPersonDiscrepancyRepository,
  private var bulkComparisonService: BulkComparisonEventService,
  private val objectMapper: ObjectMapper,
  private val calculationTransactionalService: CalculationTransactionalService,
  private val comparisonDiscrepancyService: ComparisonDiscrepancyService,
) {

  fun create(manualComparison: ManualComparisonInput, token: String): Comparison {
    val comparisonToCreate = transform(objectMapper.valueToTree(manualComparison) as JsonNode, serviceUserService.getUsername())
    val initialComparisonCreated = comparisonRepository.save(
      comparisonToCreate,
    )
    bulkComparisonService.processManualComparison(initialComparisonCreated.id, manualComparison.prisonerIds, token)

    return initialComparisonCreated
  }

  fun listManual(): List<ComparisonSummary> = comparisonRepository.findAllByComparisonTypeIsIn(manualComparisonTypes()).map { transform(it) }

  fun getCountOfPersonsInComparisonByComparisonReference(shortReference: String): Long = comparisonRepository.findByComparisonShortReference(shortReference)?.let {
    comparisonPersonRepository.countByComparisonId(it.id)
  } ?: 0

  fun getComparisonByComparisonReference(comparisonReference: String): ComparisonOverview {
    val comparison = comparisonRepository.findByComparisonShortReference(comparisonReference) ?: throw EntityNotFoundException("No comparison results exist for comparisonReference $comparisonReference ")
    val mismatches = comparisonPersonRepository.findByComparisonIdIsAndIsMatchFalse(comparison.id)
    return transform(comparison, mismatches, objectMapper)
  }

  fun getComparisonPersonByShortReference(comparisonReference: String, comparisonPersonReference: String): ComparisonPersonOverview {
    val comparison = comparisonRepository.findByComparisonShortReference(comparisonReference) ?: throw EntityNotFoundException("No comparison results exist for comparisonReference $comparisonReference ")
    val comparisonPerson = comparisonPersonRepository.findByComparisonIdAndShortReference(comparison.id, comparisonPersonReference) ?: throw EntityNotFoundException("No comparison person results exist for comparisonReference $comparisonReference and comparisonPersonReference $comparisonPersonReference ")
    val hasDiscrepancyRecord = comparisonPersonDiscrepancyRepository.existsByComparisonPerson(comparisonPerson)
    val calculatedReleaseDates = comparisonPerson.calculationRequestId?.let { calculationTransactionalService.findCalculationResults(it) }
    val nomisDates = objectMapper.convertValue(comparisonPerson.nomisDates, object : TypeReference<Map<ReleaseDateType, LocalDate?>>() {})
    val overrideDates = objectMapper.convertValue(comparisonPerson.overrideDates, object : TypeReference<Map<ReleaseDateType, LocalDate?>>() {})
    val breakdownByReleaseDateType = objectMapper.convertValue(comparisonPerson.breakdownByReleaseDateType, object : TypeReference<Map<ReleaseDateType, ReleaseDateCalculationBreakdown>>() {})
    val sdsPlusSentences = if (comparisonPerson.sdsPlusSentencesIdentified.isEmpty) emptyList() else objectMapper.convertValue(comparisonPerson.sdsPlusSentencesIdentified, object : TypeReference<List<SentenceAndOffenceWithReleaseArrangements>>() {})
    return transform(comparisonPerson, nomisDates, calculatedReleaseDates, overrideDates, breakdownByReleaseDateType, sdsPlusSentences, hasDiscrepancyRecord, objectMapper)
  }

  fun createDiscrepancy(comparisonReference: String, comparisonPersonReference: String, discrepancyInput: CreateComparisonDiscrepancyRequest): ComparisonDiscrepancySummary {
    val comparison = comparisonRepository.findByComparisonShortReference(comparisonReference) ?: throw EntityNotFoundException("No comparison results exist for comparisonReference $comparisonReference ")
    val comparisonPerson = comparisonPersonRepository.findByComparisonIdAndShortReference(comparison.id, comparisonPersonReference) ?: throw EntityNotFoundException("No comparison person results exist for comparisonReference $comparisonReference and comparisonPersonReference $comparisonPersonReference ")
    return comparisonDiscrepancyService.createDiscrepancy(comparison, comparisonPerson, discrepancyInput)
  }

  fun getComparisonPersonDiscrepancy(comparisonReference: String, comparisonPersonReference: String): ComparisonDiscrepancySummary {
    val comparison = comparisonRepository.findByComparisonShortReference(comparisonReference) ?: throw EntityNotFoundException("No comparison results exist for comparisonReference $comparisonReference ")
    val comparisonPerson = comparisonPersonRepository.findByComparisonIdAndShortReference(comparison.id, comparisonPersonReference) ?: throw EntityNotFoundException("No comparison person results exist for comparisonReference $comparisonReference and comparisonPersonReference $comparisonPersonReference ")
    return comparisonDiscrepancyService.getComparisonPersonDiscrepancy(comparison, comparisonPerson)
  }

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}
