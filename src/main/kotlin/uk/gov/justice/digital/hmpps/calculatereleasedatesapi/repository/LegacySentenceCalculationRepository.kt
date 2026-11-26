package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.LegacySentenceCalculation
import java.util.Optional

@Repository
interface LegacySentenceCalculationRepository : JpaRepository<LegacySentenceCalculation, Long> {

  @Modifying
  @Query("DELETE FROM LegacySentenceCalculation WHERE prisonerId = :prisonerId")
  fun deleteAllByPrisonerId(prisonerId: String)

  @Query("SELECT c FROM LegacySentenceCalculation c WHERE c.prisonerId = :prisonerId")
  fun getByPrisonerId(prisonerId: String): List<LegacySentenceCalculation>

  @Query("UPDATE LegacySentenceCalculation SET prisonerId = :toPrisoner WHERE prisonerId = :fromPrisoner")
  @Modifying
  fun mergePrisoners(fromPrisoner: String, toPrisoner: String)

  @Query("UPDATE LegacySentenceCalculation SET prisonerId = :toPrisoner WHERE bookingId = :bookingId and prisonerId = :fromPrisoner")
  @Modifying
  fun moveBooking(bookingId: Long, fromPrisoner: String, toPrisoner: String)

  fun findFirstByBookingIdOrderByCalculationDateDesc(bookingId: Long): Optional<LegacySentenceCalculation>
}
