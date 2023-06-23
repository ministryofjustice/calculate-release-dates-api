package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.Comparison
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.ComparisonInput
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.ComparisonPersonRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.ComparisonRepository

@Service
class ComparisonService(
  private var comparisonRepository: ComparisonRepository,
  private var prisonService: PrisonService,
  private var serviceUserService: ServiceUserService,
  private val comparisonPersonRepository: ComparisonPersonRepository,
) {

  fun create(comparison: ComparisonInput): Comparison {
    return comparisonRepository.save(
      transform(comparison, serviceUserService.getUsername()),
    )
  }

  fun listManual(): List<Comparison> {
    return comparisonRepository.findAllByManualInput(boolean = true)
  }

  fun listComparisons(): List<Comparison> {
    return comparisonRepository.findAllByManualInputAndPrisonIsIn(
      boolean = false,
      prisonService.getCurrentUserPrisonsList(),
    )
  }

  fun getCountOfPersonsInComparisonByComparisonReference(shortReference: String): Long {
    val comparison = comparisonRepository.findByComparisonShortReference(shortReference)

    if (comparison != null) {
      if (comparison.manualInput && serviceUserService.hasRoles(
          listOf(
              "ROLE_RELEASE_DATE_MANUAL_COMPARER",
              "SYSTEM_USER",
            ),
        )
      ) {
        return comparisonPersonRepository.countByComparisonId(comparison.id)
      } else {
        if (comparison.prison == null || prisonService.getCurrentUserPrisonsList().contains(comparison.prison)) {
          return comparisonPersonRepository.countByComparisonId(comparison.id)
        }
      }
    }
    return 0
  }
}
