package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.BankHolidayCache
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BankHolidays
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.BankHolidayCacheRepository
import java.time.LocalDate
import java.time.LocalDateTime
import javax.transaction.Transactional

@Service
class BankHolidayService(
  private val bankHolidayApiClient: BankHolidayApiClient,
  private val bankHolidayCacheRepository: BankHolidayCacheRepository,
  private val objectMapper: ObjectMapper
) {

  @Transactional
  fun refreshCache() {
    var cached = bankHolidayCacheRepository.findFirstByOrderByIdAsc()
    if (cached == null || cached.cachedAt.toLocalDate().isBefore(LocalDate.now())) {

      log.info("Fetching latest bank holiday data")
      val bankHolidays = bankHolidayApiClient.getBankHolidays()

      if (cached == null) {
        log.info("Persisting bank holiday data for the first time")
        cached = BankHolidayCache(cachedAt = LocalDateTime.now(), data = objectToJson(bankHolidays, objectMapper))
      } else {
        log.info("Updating bank holiday data")
        cached.cachedAt = LocalDateTime.now()
        cached.data = objectToJson(bankHolidays, objectMapper)
      }

      bankHolidayCacheRepository.save(cached)
    }
  }

  @Transactional
  fun getBankHolidays(): BankHolidays {
    val cached = bankHolidayCacheRepository.findFirstByOrderByIdAsc()

    return if (cached == null) {
      val bankHolidays = bankHolidayApiClient.getBankHolidays()
      val cache = BankHolidayCache(cachedAt = LocalDateTime.now(), data = objectToJson(bankHolidays, objectMapper))
      bankHolidayCacheRepository.save(cache)
      bankHolidays
    } else {
      objectMapper.convertValue(cached.data, BankHolidays::class.java)
    }
  }

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}
