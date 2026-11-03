package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.ApprovedDatesSubmission
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.PrisonerModifiedReason
import java.time.LocalDateTime

@Repository
interface ApprovedDatesSubmissionRepository : JpaRepository<ApprovedDatesSubmission, Long> {

  @Query("SELECT ads FROM ApprovedDatesSubmission ads WHERE ads.calculationRequest.id = :calculationRequestId")
  fun findByCalculationRequestId(calculationRequestId: Long): ApprovedDatesSubmission?

  @Query("UPDATE ApprovedDatesSubmission SET prisonerId = :toPrisoner, prisonerModifiedReason = :reason, prisonerModifiedPreviousPrisonerId = :fromPrisoner, prisonerModifiedAt = :timestamp WHERE bookingId = :bookingId and prisonerId = :fromPrisoner")
  @Modifying
  fun moveBooking(bookingId: Long, fromPrisoner: String, toPrisoner: String, reason: PrisonerModifiedReason, timestamp: LocalDateTime = LocalDateTime.now())

  @Query("UPDATE ApprovedDatesSubmission SET prisonerId = :toPrisoner, prisonerModifiedReason = :reason, prisonerModifiedPreviousPrisonerId = :fromPrisoner, prisonerModifiedAt = :timestamp WHERE prisonerId = :fromPrisoner")
  @Modifying
  fun mergePrisoners(fromPrisoner: String, toPrisoner: String, reason: PrisonerModifiedReason, timestamp: LocalDateTime = LocalDateTime.now())
}
