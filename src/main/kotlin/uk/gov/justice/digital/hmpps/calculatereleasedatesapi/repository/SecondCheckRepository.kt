package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationRequestSecondCheck

@Repository
interface SecondCheckRepository : JpaRepository<CalculationRequestSecondCheck, Long> {
  fun findAllByPrisonerId(prisonerId: String): List<CalculationRequestSecondCheck>

  @Query("SELECT c FROM CalculationRequestSecondCheck c WHERE c.calculationRequest.id = :calculationRequestId ORDER BY c.checkedAt DESC LIMIT 1")
  fun findLatestByCalculationRequestId(@Param("calculationRequestId") calculationRequestId: Long): CalculationRequestSecondCheck?
}
