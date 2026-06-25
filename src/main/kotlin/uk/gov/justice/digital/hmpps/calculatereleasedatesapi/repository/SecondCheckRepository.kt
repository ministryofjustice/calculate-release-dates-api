package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationRequestSecondCheck

@Repository
interface SecondCheckRepository : JpaRepository<CalculationRequestSecondCheck, Long> {
  fun findAllByPrisonerId(prisonerId: String): List<CalculationRequestSecondCheck>
}
