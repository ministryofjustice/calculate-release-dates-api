package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration

import org.mockito.kotlin.whenever
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.TestUtil
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.BankHolidayService
import kotlin.test.BeforeTest

@SpringBootTest()
@ActiveProfiles("test-no-db")
open class IntegrationTestBaseNoDbNoApi internal constructor() {

  @MockitoBean
  private lateinit var bankHolidayService: BankHolidayService

  @BeforeTest
  fun setup() {
    whenever(bankHolidayService.getBankHolidays()).thenReturn(TestUtil.defaultBankHolidays())
  }

  companion object {

    @JvmStatic
    @DynamicPropertySource
    fun properties(registry: DynamicPropertyRegistry) {
      System.setProperty("aws.region", "eu-west-2")
    }
  }
}
