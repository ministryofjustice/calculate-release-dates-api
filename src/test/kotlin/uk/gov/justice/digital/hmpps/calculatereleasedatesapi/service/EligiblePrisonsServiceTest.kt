package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.client.PrisonRegisterApiClient
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.CrdWebException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.MaxRetryAchievedException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonRegisterPrison

class EligiblePrisonsServiceTest {
  private val prisonService: PrisonService = mock()
  private val prisonRegisterApiClient: PrisonRegisterApiClient = mock()
  private val service = EligiblePrisonsService(prisonService, prisonRegisterApiClient)

  @Nested
  inner class EligibleCaseloadPrisons {

    @Test
    fun `bulk all is the caseload intersected with active register prisons`() {
      whenever(prisonService.getCurrentUserPrisonsList()).thenReturn(listOf("MDI", "LEI", "DAI"))
      whenever(prisonRegisterApiClient.getActivePrisons()).thenReturn(listOf(prison("MDI"), prison("LEI"), prison("BXI")))

      val result = service.eligibleCaseloadPrisons()

      assertThat(result.eligible).containsExactly("MDI", "LEI")
      assertThat(result.inactive).containsExactly("DAI")
      assertThat(result.eligible).doesNotContain("BXI")
      assertThat(result.registerAvailable).isTrue()
    }

    @Test
    fun `an empty register answer is authoritative and does not fall back to the caseload`() {
      whenever(prisonService.getCurrentUserPrisonsList()).thenReturn(listOf("MDI", "LEI"))
      whenever(prisonRegisterApiClient.getActivePrisons()).thenReturn(emptyList())

      val result = service.eligibleCaseloadPrisons()

      assertThat(result.eligible).isEmpty()
      assertThat(result.inactive).containsExactly("MDI", "LEI")
      assertThat(result.registerAvailable).isTrue()
    }

    @Test
    fun `an unavailable register falls back to the whole caseload`() {
      whenever(prisonService.getCurrentUserPrisonsList()).thenReturn(listOf("MDI", "DAI"))
      whenever(prisonRegisterApiClient.getActivePrisons()).thenThrow(MaxRetryAchievedException("Max retries - getActivePrisons()"))

      val result = service.eligibleCaseloadPrisons()

      assertThat(result.eligible).containsExactly("MDI", "DAI")
      assertThat(result.inactive).isEmpty()
      assertThat(result.registerAvailable).isFalse()
    }

    @Test
    fun `an auth failure from the register is not treated as unavailable`() {
      whenever(prisonService.getCurrentUserPrisonsList()).thenReturn(listOf("MDI"))
      whenever(prisonRegisterApiClient.getActivePrisons()).thenThrow(WebClientResponseException(403, "Forbidden", null, null, null))

      assertThatThrownBy { service.eligibleCaseloadPrisons() }.isInstanceOf(WebClientResponseException::class.java)
    }

    @Test
    fun `inactive entries in the register response are ignored`() {
      whenever(prisonService.getCurrentUserPrisonsList()).thenReturn(listOf("MDI", "DAI"))
      whenever(prisonRegisterApiClient.getActivePrisons()).thenReturn(listOf(prison("MDI"), prison("DAI", active = false)))

      assertThat(service.eligibleCaseloadPrisons().eligible).containsExactly("MDI")
    }
  }

  @Nested
  inner class CheckPrisonCanBeCompared {

    @Test
    fun `an active prison is allowed`() {
      whenever(prisonRegisterApiClient.getPrison("MDI")).thenReturn(prison("MDI"))

      assertThatCode { service.checkPrisonCanBeCompared("MDI") }.doesNotThrowAnyException()
    }

    @Test
    fun `an inactive prison is rejected with a 400`() {
      whenever(prisonRegisterApiClient.getPrison("DAI")).thenReturn(prison("DAI", active = false))

      assertThatThrownBy { service.checkPrisonCanBeCompared("DAI") }
        .isInstanceOf(CrdWebException::class.java)
        .hasMessage("Bulk calculation cannot be run for inactive prison DAI.")
        .extracting("status").isEqualTo(HttpStatus.BAD_REQUEST)
    }

    @Test
    fun `an unknown prison is rejected with a 400 and a different code`() {
      whenever(prisonRegisterApiClient.getPrison("ZZZ")).thenReturn(null)

      assertThatThrownBy { service.checkPrisonCanBeCompared("ZZZ") }
        .isInstanceOf(CrdWebException::class.java)
        .extracting("code").isEqualTo("UNKNOWN_PRISON")
    }

    @Test
    fun `an unavailable register lets the request through`() {
      whenever(prisonRegisterApiClient.getPrison("MDI")).thenThrow(MaxRetryAchievedException("Max retries - getPrison(MDI)"))

      assertThatCode { service.checkPrisonCanBeCompared("MDI") }.doesNotThrowAnyException()
    }
  }

  private fun prison(id: String, active: Boolean = true) = PrisonRegisterPrison(prisonId = id, prisonName = "$id prison", active = active)
}
