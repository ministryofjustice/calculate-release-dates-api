package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository

import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.EntityGraph.EntityGraphType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationStatus
import java.util.Optional
import java.util.UUID

@Repository
interface CalculationRequestRepository : JpaRepository<CalculationRequest, Long> {

  @EntityGraph(value = "CalculationRequest.detail", type = EntityGraphType.LOAD)
  fun findFirstByPrisonerIdAndBookingIdAndCalculationStatusOrderByCalculatedAtDesc(
    prisonerId: String,
    bookingId: Long,
    calculationStatus: String,
  ): Optional<CalculationRequest>

  @EntityGraph(value = "CalculationRequest.detail", type = EntityGraphType.LOAD)
  fun findByIdAndCalculationStatus(calculationRequestId: Long, calculationStatus: String): Optional<CalculationRequest>

  @EntityGraph(value = "CalculationRequest.detail", type = EntityGraphType.LOAD)
  fun findByCalculationReference(calculationReference: UUID): Optional<CalculationRequest>

  @EntityGraph(value = "CalculationRequest.detail", type = EntityGraphType.LOAD)
  fun findAllByPrisonerIdAndCalculationStatus(prisonerId: String, calculationStatus: String): List<CalculationRequest>

  @EntityGraph(value = "CalculationRequest.detail", type = EntityGraphType.LOAD)
  fun findFirstByBookingIdAndCalculationStatusOrderByCalculatedAtDesc(bookingId: Long, status: String = CalculationStatus.CONFIRMED.name): Optional<CalculationRequest>

  @EntityGraph(value = "CalculationRequest.detail", type = EntityGraphType.LOAD)
  fun findFirstByPrisonerIdAndCalculationStatusOrderByCalculatedAtDesc(prisonerId: String, status: String = CalculationStatus.CONFIRMED.name): Optional<CalculationRequest>
}
