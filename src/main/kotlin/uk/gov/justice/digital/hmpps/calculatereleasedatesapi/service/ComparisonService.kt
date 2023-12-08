package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.persistence.EntityNotFoundException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationOutcome
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.Comparison
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.ComparisonPerson
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.CrdWebException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ComparisonOverview
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ComparisonPersonOverview
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ComparisonSummary
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.MismatchType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ReleaseDateCalculationBreakdown
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.ComparisonInput
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationOutcomeRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.ComparisonPersonRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.ComparisonRepository
import java.time.LocalDate

@Service
class ComparisonService(
  private var calculationOutcomeRepository: CalculationOutcomeRepository,
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
      val mismatches = comparisonPersonRepository.findByComparisonIdIsAndIsMatchFalse(comparison.id)

      val releaseMismatchCalculationRequests = mismatches
        .filter { it.mismatchType == MismatchType.RELEASE_DATES_MISMATCH }
        .mapNotNull { it.calculationRequestId }

      val calculationOutcomes = if (releaseMismatchCalculationRequests.isNotEmpty()) {
        calculationOutcomeRepository.findByCalculationRequestIdIn(releaseMismatchCalculationRequests)
      } else {
        emptyList()
      }

      val requestIdsToCalculationOutcomes = calculationOutcomes.groupBy { it.calculationRequestId }
      val mismatchesAndCrdsDates = mismatches.map { mismatch ->
        if (requestIdsToCalculationOutcomes[mismatch.calculationRequestId] != null) {
          Pair(mismatch, requestIdsToCalculationOutcomes[mismatch.calculationRequestId]!!)
        } else {
          Pair(mismatch, emptyList())
        }
      }

      val mismatchesSortedByReleaseDate = mismatchesAndCrdsDates.sortedWith(::releaseDateComparator)
      return transform(comparison, mismatchesSortedByReleaseDate.map { it.first })
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
      val sdsCaseAndCount = listOf("Court case 1, count 3", "Court case 2, count 1")
      return transform(comparisonPerson, nomisDates, calculatedReleaseDates, overrideDates, breakdownByReleaseDateType, sdsCaseAndCount)
    }
    throw CrdWebException("Forbidden", HttpStatus.FORBIDDEN, 403.toString())
  }

  private fun releaseDateComparator(
    mismatchA: Pair<ComparisonPerson, List<CalculationOutcome>>,
    mismatchB: Pair<ComparisonPerson, List<CalculationOutcome>>,
  ): Int {
    val releaseDatesA = mismatchA.second
    val releaseDatesB = mismatchB.second

    val includedDates =
      listOf(ReleaseDateType.CRD.name, ReleaseDateType.ARD.name, ReleaseDateType.PRRD.name, ReleaseDateType.MTD.name)

    val applicableDatesA = releaseDatesA.filter { it.calculationDateType in includedDates }
    val applicableDatesB = releaseDatesB.filter { it.calculationDateType in includedDates }

    val earliestReleaseDateA = applicableDatesA.mapNotNull { it.outcomeDate }.minByOrNull { it }
    val earliestReleaseDateB = applicableDatesB.mapNotNull { it.outcomeDate }.minByOrNull { it }

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
