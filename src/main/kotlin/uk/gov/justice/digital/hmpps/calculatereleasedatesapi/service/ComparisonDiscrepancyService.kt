package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import jakarta.persistence.EntityNotFoundException
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.Comparison
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.ComparisonPerson
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.ComparisonPersonDiscrepancy
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.ComparisonPersonDiscrepancyCause
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.ComparisonPersonDiscrepancyImpact
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.ComparisonPersonDiscrepancyPriority
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ComparisonDiscrepancySummary
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CreateComparisonDiscrepancyRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.ComparisonPersonDiscrepancyCategoryRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.ComparisonPersonDiscrepancyRepository

@Service
class ComparisonDiscrepancyService(
  private val comparisonPersonDiscrepancyRepository: ComparisonPersonDiscrepancyRepository,
  private val comparisonPersonDiscrepancyCategoryRepository: ComparisonPersonDiscrepancyCategoryRepository,
  private var serviceUserService: ServiceUserService,
) {

  @Transactional
  fun createDiscrepancy(
    comparison: Comparison,
    comparisonPerson: ComparisonPerson,
    discrepancyRequest: CreateComparisonDiscrepancyRequest,
  ): ComparisonDiscrepancySummary {
    val existingDiscrepancy =
      comparisonPersonDiscrepancyRepository.findTopByComparisonPersonShortReferenceAndSupersededByIdIsNullOrderByCreatedAtDesc(
        comparisonPerson.shortReference,
      )

    val impact = ComparisonPersonDiscrepancyImpact(discrepancyRequest.impact)
    val priority = ComparisonPersonDiscrepancyPriority(discrepancyRequest.priority)
    var discrepancy = ComparisonPersonDiscrepancy(
      comparisonPerson = comparisonPerson,
      discrepancyImpact = impact,
      discrepancyPriority = priority,
      action = discrepancyRequest.action,
      detail = discrepancyRequest.detail,
      createdBy = serviceUserService.getUsername(),
    )
    discrepancy = comparisonPersonDiscrepancyRepository.save(discrepancy)
    if (existingDiscrepancy != null) {
      existingDiscrepancy.supersededById = discrepancy.id
      comparisonPersonDiscrepancyRepository.save(existingDiscrepancy)
    }

    val discrepancyCauses = discrepancyRequest.causes.map {
      ComparisonPersonDiscrepancyCause(
        category = it.category,
        subCategory = it.subCategory,
        detail = it.other,
        discrepancy = discrepancy,
      )
    }
    comparisonPersonDiscrepancyCategoryRepository.saveAll(discrepancyCauses)
    return transform(discrepancy, discrepancyCauses)
  }

  fun getComparisonPersonDiscrepancy(
    comparison: Comparison,
    comparisonPerson: ComparisonPerson,
  ): ComparisonDiscrepancySummary {
    val discrepancy =
      comparisonPersonDiscrepancyRepository.findTopByComparisonPersonShortReferenceAndSupersededByIdIsNullOrderByCreatedAtDesc(
        comparisonPerson.shortReference,
      ) ?: throw EntityNotFoundException("No comparison person discrepancy was found")
    return transform(discrepancy)
  }
}
