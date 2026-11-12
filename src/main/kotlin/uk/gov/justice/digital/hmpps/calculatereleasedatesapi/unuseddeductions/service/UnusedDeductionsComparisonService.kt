package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.unuseddeductions.service

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import jakarta.persistence.EntityNotFoundException
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ComparisonStatus
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.CrdWebException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.PrisonService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.ServiceUserService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.unuseddeductions.entity.UnusedDeductionsComparison
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.unuseddeductions.entity.UnusedDeductionsComparisonOutcome
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.unuseddeductions.entity.UnusedDeductionsComparisonPerson
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.unuseddeductions.entity.UnusedDeductionsComparisonType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.unuseddeductions.model.UnusedDeductionsAdjustmentInput
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.unuseddeductions.model.UnusedDeductionsComparisonInput
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.unuseddeductions.model.UnusedDeductionsComparisonOverview
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.unuseddeductions.model.UnusedDeductionsComparisonPage
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.unuseddeductions.model.UnusedDeductionsComparisonPersonDetail
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.unuseddeductions.model.UnusedDeductionsComparisonPersonSummary
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.unuseddeductions.model.UnusedDeductionsComparisonSummary
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.unuseddeductions.model.UnusedDeductionsPageInfo
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.unuseddeductions.repository.UnusedDeductionsComparisonPersonRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.unuseddeductions.repository.UnusedDeductionsComparisonRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationMessage

@Service
class UnusedDeductionsComparisonService(
  private val comparisonRepository: UnusedDeductionsComparisonRepository,
  private val comparisonPersonRepository: UnusedDeductionsComparisonPersonRepository,
  private val senderService: UnusedDeductionsComparisonSenderService,
  private val prisonService: PrisonService,
  private val serviceUserService: ServiceUserService,
  private val objectMapper: ObjectMapper,
) {

  fun create(input: UnusedDeductionsComparisonInput, token: String): UnusedDeductionsComparisonSummary {
    val prisonerIds = input.prisonerIds?.map { it.trim().uppercase() }?.filter { it.isNotEmpty() }?.distinct()
    val prison = input.prison?.trim()?.takeIf { it.isNotEmpty() }
    val type = when {
      !prisonerIds.isNullOrEmpty() -> UnusedDeductionsComparisonType.MANUAL
      prison == ALL_PRISONS -> UnusedDeductionsComparisonType.ALL_PRISONS
      prison != null -> UnusedDeductionsComparisonType.PRISON
      else -> throw CrdWebException("Provide a list of prisoner ids, a prison code or 'all'", HttpStatus.BAD_REQUEST, "400")
    }
    if (type == UnusedDeductionsComparisonType.PRISON && !prisonService.getCurrentUserPrisonsList().contains(prison)) {
      throw CrdWebException("Forbidden", HttpStatus.FORBIDDEN, "403")
    }

    val comparison = comparisonRepository.save(
      UnusedDeductionsComparison(
        criteria = objectMapper.valueToTree(
          mapOf(
            "prison" to prison.takeIf { type != UnusedDeductionsComparisonType.MANUAL },
            "prisonerIds" to prisonerIds.takeIf { type == UnusedDeductionsComparisonType.MANUAL },
          ),
        ),
        prison = prison.takeIf { type != UnusedDeductionsComparisonType.MANUAL },
        comparisonType = type,
        calculatedByUsername = serviceUserService.getUsername(),
      ),
    )
    when (type) {
      UnusedDeductionsComparisonType.MANUAL -> senderService.processManual(comparison.id(), prisonerIds!!, token)
      UnusedDeductionsComparisonType.PRISON -> senderService.processPrison(comparison.id(), prison!!, token)
      UnusedDeductionsComparisonType.ALL_PRISONS -> senderService.processAllPrisons(comparison.id(), token)
    }
    return summarise(comparison)
  }

  fun list(page: Int = 0, size: Int = DEFAULT_LIST_PAGE_SIZE, calculatedByCurrentUser: Boolean? = null): UnusedDeductionsComparisonPage {
    val prisons = prisonService.getCurrentUserPrisonsList()
    val pageNumber = page.coerceAtLeast(0)
    val pageSize = size.coerceIn(1, MAX_PAGE_SIZE)
    val username = serviceUserService.getUsername()
    val pageRequest = PageRequest.of(pageNumber, pageSize)
    val result = when (calculatedByCurrentUser) {
      null -> comparisonRepository.findViewable(prisons, pageRequest)
      true -> comparisonRepository.findViewableCalculatedBy(prisons, username, pageRequest)
      false -> comparisonRepository.findViewableNotCalculatedBy(prisons, username, pageRequest)
    }
    return UnusedDeductionsComparisonPage(
      content = result.content.map { summarise(it) },
      page = UnusedDeductionsPageInfo(pageNumber, pageSize, result.totalElements, result.totalPages),
    )
  }

  fun get(
    reference: String,
    outcome: UnusedDeductionsComparisonOutcome? = null,
    prisonerId: String? = null,
    page: Int = 0,
    size: Int = DEFAULT_PAGE_SIZE,
  ): UnusedDeductionsComparisonOverview {
    val comparison = findViewable(reference)
    val people = comparisonPersonRepository.findByComparisonId(comparison.id())
    val pageNumber = page.coerceAtLeast(0)
    val pageSize = size.coerceIn(1, MAX_PAGE_SIZE)
    val idFilter = prisonerId?.trim()?.takeIf { it.isNotEmpty() }
    val matching = people.map { it.toSummary() }
      .filter { outcome == null || it.outcome == outcome }
      .filter { idFilter == null || it.person.contains(idFilter, ignoreCase = true) }
      .sortedWith(compareBy({ it.outcome == UnusedDeductionsComparisonOutcome.MATCH }, { it.outcome.ordinal }, { it.person }))
    return UnusedDeductionsComparisonOverview(
      comparison = summarise(comparison, people),
      criteria = objectMapper.convertValue(comparison.criteria, object : TypeReference<Map<String, Any?>>() {}),
      people = matching.drop(pageNumber * pageSize).take(pageSize),
      page = UnusedDeductionsPageInfo(pageNumber, pageSize, matching.size.toLong(), (matching.size + pageSize - 1) / pageSize),
    )
  }

  fun getPerson(reference: String, personReference: String): UnusedDeductionsComparisonPersonDetail {
    val comparison = findViewable(reference)
    val person = comparisonPersonRepository.findByComparisonIdAndShortReference(comparison.id(), personReference)
      ?: throw EntityNotFoundException("No unused deductions comparison person results exist for $reference and $personReference")
    return UnusedDeductionsComparisonPersonDetail(
      comparison = summarise(comparison),
      person = person.toSummary(),
      adjustments = objectMapper.readValue<List<UnusedDeductionsAdjustmentInput>>(person.adjustments.toString()),
      validationMessages = objectMapper.readValue<List<ValidationMessage>>(person.validationMessages.toString()),
      fatalException = person.fatalException,
      calculatedAt = person.calculatedAt,
    )
  }

  private fun findViewable(reference: String): UnusedDeductionsComparison {
    val comparison = comparisonRepository.findByComparisonShortReference(reference)
      ?: throw EntityNotFoundException("No unused deductions comparison results exist for comparisonReference $reference")
    if (!canView(comparison, prisonService.getCurrentUserPrisonsList())) {
      throw CrdWebException("Forbidden", HttpStatus.FORBIDDEN, "403")
    }
    return comparison
  }

  private fun canView(comparison: UnusedDeductionsComparison, userPrisons: List<String>) = comparison.comparisonType != UnusedDeductionsComparisonType.PRISON || userPrisons.contains(comparison.prison)

  private fun summarise(comparison: UnusedDeductionsComparison, people: List<UnusedDeductionsComparisonPerson> = comparisonPersonRepository.findByComparisonId(comparison.id())) = UnusedDeductionsComparisonSummary(
    comparisonShortReference = comparison.comparisonShortReference,
    prison = comparison.prison,
    comparisonType = comparison.comparisonType,
    comparisonStatus = comparison.comparisonStatus,
    calculatedAt = comparison.calculatedAt,
    calculatedByUsername = comparison.calculatedByUsername,
    numberOfMismatches = people.count { it.outcome == UnusedDeductionsComparisonOutcome.MISMATCH }.toLong(),
    numberOfValidationErrors = people.count { it.outcome == UnusedDeductionsComparisonOutcome.VALIDATION_ERROR }.toLong(),
    // While still in setup the expected total is only a placeholder, as messages are already being processed.
    numberOfPeopleExpected = if (comparison.comparisonStatus == ComparisonStatus.SETUP) maxOf(comparison.numberOfPeopleExpected, people.size.toLong()) else comparison.numberOfPeopleExpected,
    numberOfPeopleCompared = if (comparison.comparisonStatus == ComparisonStatus.PROCESSING || comparison.comparisonStatus == ComparisonStatus.SETUP) people.size.toLong() else comparison.numberOfPeopleCompared,
    numberOfPeopleComparisonFailedFor = people.count { it.outcome == UnusedDeductionsComparisonOutcome.FATAL_ERROR }.toLong(),
  )

  private fun UnusedDeductionsComparisonPerson.toSummary() = UnusedDeductionsComparisonPersonSummary(
    personShortReference = shortReference,
    person = person,
    lastName = lastName,
    establishment = establishment,
    outcome = outcome,
    existingUnusedDeductions = existingUnusedDeductions,
    calculatedUnusedDeductions = calculatedUnusedDeductions,
    custodialDays = custodialDays,
    totalDeductions = totalDeductions,
  )

  // Problems are listed ahead of matches.
  private companion object {
    const val ALL_PRISONS = "all"
    const val DEFAULT_PAGE_SIZE = 50
    const val DEFAULT_LIST_PAGE_SIZE = 10
    const val MAX_PAGE_SIZE = 500
  }
}
