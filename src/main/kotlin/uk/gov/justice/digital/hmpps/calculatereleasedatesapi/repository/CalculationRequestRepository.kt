package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository

import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.EntityGraph.EntityGraphType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
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

  @Query(
    """
        SELECT cr
        FROM CalculationRequest cr
        JOIN FETCH cr.calculationOutcomes
        WHERE cr.prisonerId = :prisonerId
          AND cr.calculationStatus = :calculationStatus
          AND cr.manualCalculationReason IS NOT EMPTY
          AND cr.calculationOutcomes IS NOT EMPTY
        ORDER BY cr.id DESC
        FETCH FIRST 1 ROWS ONLY
        """,
  )
  fun findLatestManualCalculation(
    prisonerId: String,
    calculationStatus: String,
  ): CalculationRequest?

  @Query(
    """
        SELECT cr
        FROM CalculationRequest cr
        JOIN FETCH cr.approvedDatesSubmissions
        WHERE cr.prisonerId = :prisonerId
          AND cr.calculationStatus = 'CONFIRMED'
          AND cr.approvedDatesSubmissions IS NOT EMPTY
        ORDER BY cr.calculatedAt DESC
        FETCH FIRST 1 ROWS ONLY
        """,
  )
  fun findLatestCalculationWithApprovedDates(prisonerId: String): CalculationRequest?

  @Query(
    """
        SELECT cr
        FROM CalculationRequest cr
        JOIN FETCH cr.calculationOutcomes
        WHERE cr.prisonerId = :prisonerId
          AND cr.calculationStatus = 'CONFIRMED'
          AND cr.reasonForCalculation.eligibleForPreviouslyRecordedSled = true
        ORDER BY cr.calculatedAt DESC
        FETCH FIRST 1 ROWS ONLY
        """,
  )
  fun findLatestCalculationForPreviousSLED(prisonerId: String): CalculationRequest?
}
