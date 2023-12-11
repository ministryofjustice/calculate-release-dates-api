package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.persistence.EntityNotFoundException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.Comparison
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ComparisonOverview
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ComparisonPersonOverview
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ComparisonSummary
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ManualComparisonInput
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ReleaseDateCalculationBreakdown
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAndOffences
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.ComparisonPersonRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.ComparisonRepository
import java.time.LocalDate

@Service
class ManualComparisonService(
  private var comparisonRepository: ComparisonRepository,
  private var serviceUserService: ServiceUserService,
  private val comparisonPersonRepository: ComparisonPersonRepository,
  private var bulkComparisonService: BulkComparisonService,
  private val objectMapper: ObjectMapper,
  private val calculationTransactionalService: CalculationTransactionalService,
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
    val mismatches = comparisonPersonRepository.findByComparisonIdIsAndIsMatchFalse(comparison.id)
    return transform(comparison, mismatches)
  }

  fun getComparisonPersonByShortReference(comparisonReference: String, comparisonPersonReference: String): ComparisonPersonOverview {
    val comparison = comparisonRepository.findByManualInputAndComparisonShortReference(true, comparisonReference) ?: throw EntityNotFoundException("No comparison results exist for comparisonReference $comparisonReference ")
    val comparisonPerson = comparisonPersonRepository.findByComparisonIdAndShortReference(comparison.id, comparisonPersonReference) ?: throw EntityNotFoundException("No comparison person results exist for comparisonReference $comparisonReference and comparisonPersonReference $comparisonPersonReference ")
    val calculatedReleaseDates = comparisonPerson.calculationRequestId?.let { calculationTransactionalService.findCalculationResults(it) }
    val nomisDates = objectMapper.convertValue(comparisonPerson.nomisDates, object : TypeReference<Map<ReleaseDateType, LocalDate?>>() {})
    val overrideDates = objectMapper.convertValue(comparisonPerson.overrideDates, object : TypeReference<Map<ReleaseDateType, LocalDate?>>() {})
    val breakdownByReleaseDateType = objectMapper.convertValue(comparisonPerson.breakdownByReleaseDateType, object : TypeReference<Map<ReleaseDateType, ReleaseDateCalculationBreakdown>>() {})
    val sdsCaseAndCount =  buildSdsCaseAndCount(objectMapper.convertValue(comparisonPerson.sdsPlusSentencesIdentified, object : TypeReference<List<SentenceAndOffences>>() {}))
    return transform(comparisonPerson, nomisDates, calculatedReleaseDates, overrideDates, breakdownByReleaseDateType, sdsCaseAndCount)
  }

  private fun buildSdsCaseAndCount(sentencesAndOffences: List<SentenceAndOffences>): List<String> {
    val caseAndCountList = mutableListOf<String>()
    sentencesAndOffences.forEach {
      caseAndCountList.add("Court case ${it.caseSequence}, count ${it.lineSequence}")
    }
    return caseAndCountList
  }

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}
