package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationRequest
import java.util.Optional
import java.util.UUID

@Repository
interface CalculationRequestRepository : JpaRepository<CalculationRequest, Long> {
  fun findFirstByPrisonerIdAndBookingIdAndCalculationStatusOrderByCalculatedAtDesc(
    prisonerId: String,
    bookingId: Long,
    calculationStatus: String,
  ): Optional<CalculationRequest>

  fun findByIdAndCalculationStatus(calculationRequestId: Long, calculationStatus: String): Optional<CalculationRequest>

  fun findByCalculationReference(calculationReference: UUID): Optional<CalculationRequest>
  fun findAllByPrisonerIdAndCalculationStatus(prisonerId: String, calculationStatus: String): List<CalculationRequest>

  @Query(nativeQuery = true, value = "select * from calculation_request where prisoner_id in (select prisoner_id from calculation_request where booking_id = ? limit 1) order by calculated_at desc limit 1")
  fun findLatestCalculation(bookingId: Long): Optional<CalculationRequest>

  @Query(nativeQuery = true, value = "select * from calculation_request where prisoner_id = ? and calculation_status = 'CONFIRMED' order by calculated_at desc limit 1")
  fun findLatestConfirmedCalculationForPrisoner(prisonerId: String): Optional<CalculationRequest>
}
