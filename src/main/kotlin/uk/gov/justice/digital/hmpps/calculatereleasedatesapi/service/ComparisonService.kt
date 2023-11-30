package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.persistence.EntityNotFoundException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.Comparison
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.ComparisonPerson
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.CrdWebException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ComparisonOverview
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ComparisonPersonOverview
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ComparisonSummary
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ReleaseDateCalculationBreakdown
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.ComparisonInput
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.ComparisonPersonRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.ComparisonRepository
import java.time.LocalDate

@Service
class ComparisonService(
  private var comparisonRepository: ComparisonRepository,
  private var prisonService: PrisonService,
  private var serviceUserService: ServiceUserService,
  private val comparisonPersonRepository: ComparisonPersonRepository,
  private var bulkComparisonService: BulkComparisonService,
  private val calculationTransactionalService: CalculationTransactionalService,
  private val objectMapper: ObjectMapper,
) {

  fun create(comparisonInput: ComparisonInput): Comparison {
    val comparisonToCreate = transform(comparisonInput, serviceUserService.getUsername())
    log.info("Creating new comparison $comparisonToCreate")
    val initialComparisonCreated = comparisonRepository.save(
      comparisonToCreate,
    )
    bulkComparisonService.processPrisonComparison(initialComparisonCreated)
    return initialComparisonCreated
  }

  fun listComparisons(): List<ComparisonSummary> {
    return comparisonRepository.findAllByManualInputAndPrisonIsIn(
      false,
      prisonService.getCurrentUserPrisonsList(),
    ).map { transform(it) }
  }

  fun getCountOfPersonsInComparisonByComparisonReference(shortReference: String): Long {
    val comparison = comparisonRepository.findByManualInputAndComparisonShortReference(false, shortReference)

    if (comparison?.prison != null && prisonService.getCurrentUserPrisonsList().contains(comparison.prison)) {
      return comparisonPersonRepository.countByComparisonId(comparison.id)
    }
    return 0
  }

  fun getComparisonByComparisonReference(comparisonReference: String): ComparisonOverview {
    val comparison = comparisonRepository.findByManualInputAndComparisonShortReference(false, comparisonReference) ?: throw EntityNotFoundException("No comparison results exist for comparisonReference $comparisonReference ")

    if (comparison.prison != null && prisonService.getCurrentUserPrisonsList().contains(comparison.prison)) {
      val mismatches = comparisonPersonRepository.findByComparisonIdIs(comparison.id)
      val mismatchesSortedByReleaseDate = mismatches.sortedWith(::releaseDateComparator)
      return transform(comparison, mismatchesSortedByReleaseDate)
    }
    throw CrdWebException("Forbidden", HttpStatus.FORBIDDEN, 403.toString())
  }

  fun getComparisonPersonByShortReference(comparisonReference: String, comparisonPersonReference: String): ComparisonPersonOverview {
    val comparison = comparisonRepository.findByManualInputAndComparisonShortReference(false, comparisonReference) ?: throw EntityNotFoundException("No comparison results exist for comparisonReference $comparisonReference ")

    if (comparison.prison != null && prisonService.getCurrentUserPrisonsList().contains(comparison.prison)) {
      val comparisonPerson = comparisonPersonRepository.findByComparisonIdAndShortReference(comparison.id, comparisonPersonReference) ?: throw EntityNotFoundException("No comparison person results exist for comparisonReference $comparisonReference and comparisonPersonReference $comparisonPersonReference ")
      val calculatedReleaseDates = comparisonPerson.calculationRequestId?.let { calculationTransactionalService.findCalculationResults(it) }
      val nomisDates = objectMapper.convertValue(comparisonPerson.nomisDates, object : TypeReference<Map<ReleaseDateType, LocalDate?>>() {})
      val overrideDates = objectMapper.convertValue(comparisonPerson.overrideDates, object : TypeReference<Map<ReleaseDateType, LocalDate?>>() {})
      val breakdownByReleaseDateType = objectMapper.convertValue(comparisonPerson.breakdownByReleaseDateType, object : TypeReference<Map<ReleaseDateType, ReleaseDateCalculationBreakdown>>() {})
      return transform(comparisonPerson, nomisDates, calculatedReleaseDates, overrideDates, breakdownByReleaseDateType)
    }
    throw CrdWebException("Forbidden", HttpStatus.FORBIDDEN, 403.toString())
  }

  private fun releaseDateComparator(mismatchA: ComparisonPerson, mismatchB: ComparisonPerson): Int {
    val releaseDatesA = objectMapper.convertValue(mismatchA.nomisDates, object : TypeReference<Map<ReleaseDateType, LocalDate?>>() {})
    val releaseDatesB = objectMapper.convertValue(mismatchB.nomisDates, object : TypeReference<Map<ReleaseDateType, LocalDate?>>() {})

    val earliestReleaseDateA = releaseDatesA.values.filterNotNull().minOrNull()
    val earliestReleaseDateB = releaseDatesB.values.filterNotNull().minOrNull()

    if (earliestReleaseDateA === null && earliestReleaseDateB == null) {
      return 0
    }

    if (earliestReleaseDateA == null) {
      return 1
    }

    if (earliestReleaseDateB == null) {
      return -1
    }

    return earliestReleaseDateA.compareTo(earliestReleaseDateB)
  }

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}
