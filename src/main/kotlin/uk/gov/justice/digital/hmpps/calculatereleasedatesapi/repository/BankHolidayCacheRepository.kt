package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.BankHolidayCache

@Repository
interface BankHolidayCacheRepository : JpaRepository<BankHolidayCache, Long> {

  fun findFirstByOrderByIdAsc(): BankHolidayCache?
}
