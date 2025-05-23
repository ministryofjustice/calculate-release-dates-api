package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.BankHolidayCache
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BankHolidays
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.BankHolidayCacheRepository
import java.time.LocalDateTime

@Service
class BankHolidayTransactionService(
  private val bankHolidayApiClient: BankHolidayApiClient,
  private val bankHolidayCacheRepository: BankHolidayCacheRepository,
  private val objectMapper: ObjectMapper,
) {
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  fun saveNewBankHolidays(): BankHolidays {
    val bankHolidays = bankHolidayApiClient.getBankHolidays()
    val cache = BankHolidayCache(
      cachedAt = LocalDateTime.now(),
      data = objectToJson(bankHolidays, objectMapper),
    )
    bankHolidayCacheRepository.save(cache)
    return bankHolidays
  }
}
