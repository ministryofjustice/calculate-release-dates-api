package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.MaxRetryAchievedException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.prisonapi.model.CalculablePrisoner

class BulkComparisonEventSenderServiceTest {
  private val prisonService: PrisonService = mock()
  private val publisher: BulkComparisonEventPublisher = mock()
  private val serviceUserService: ServiceUserService = mock()
  private val completer: BulkComparisonSetupCompleter = mock()
  private val eligiblePrisonsService: EligiblePrisonsService = mock()
  private val sender = BulkComparisonEventSenderService(prisonService, publisher, serviceUserService, completer, eligiblePrisonsService)

  @BeforeEach
  fun setUp() {
    whenever(serviceUserService.getUsername()).thenReturn("USER")
  }

  @Test
  fun `inactive caseload prisons are not queried`() {
    givenEligible(eligible = listOf("MDI"), inactive = listOf("DAI"))
    whenever(prisonService.getCalculablePrisonerByPrison("MDI")).thenReturn(listOf(prisoner("A1")))

    sender.processFullCaseLoadComparison(1, "token")

    verify(prisonService, never()).getCalculablePrisonerByPrison("DAI")
    verify(completer).completeSetup(1, 1, emptyList())
  }

  @Test
  fun `a 404 for one prison skips that prison and records it as failed`() {
    givenEligible(eligible = listOf("MDI", "LEI"))
    whenever(prisonService.getCalculablePrisonerByPrison("MDI")).thenReturn(listOf(prisoner("A1"), prisoner("A2")))
    whenever(prisonService.getCalculablePrisonerByPrison("LEI")).thenThrow(WebClientResponseException(404, "Not found", null, null, null))

    sender.processFullCaseLoadComparison(1, "token")

    verify(publisher).sendMessageBatch(eq(1L), eq(listOf("A1", "A2")), eq("USER"), eq("MDI"))
    verify(completer).completeSetup(1, 2, listOf("LEI"))
    verify(completer, never()).handleErrorInBulkSetup(any(), any())
  }

  @Test
  fun `a 5xx that survives retries skips that prison`() {
    givenEligible(eligible = listOf("MDI", "LEI"))
    whenever(prisonService.getCalculablePrisonerByPrison("MDI")).thenReturn(listOf(prisoner("A1")))
    whenever(prisonService.getCalculablePrisonerByPrison("LEI")).thenThrow(MaxRetryAchievedException("Max retries"))

    sender.processFullCaseLoadComparison(1, "token")

    verify(completer).completeSetup(1, 1, listOf("LEI"))
  }

  @Test
  fun `a 403 fails the whole run and sends nothing`() {
    givenEligible(eligible = listOf("MDI", "LEI"))
    whenever(prisonService.getCalculablePrisonerByPrison("MDI")).thenReturn(listOf(prisoner("A1")))
    whenever(prisonService.getCalculablePrisonerByPrison("LEI")).thenThrow(WebClientResponseException(403, "Forbidden", null, null, null))

    sender.processFullCaseLoadComparison(1, "token")

    verify(publisher, never()).sendMessageBatch(any(), any(), any(), anyOrNull())
    verify(completer).handleErrorInBulkSetup(eq(1L), any())
  }

  @Test
  fun `an unexpected exception fails the whole run`() {
    givenEligible(eligible = listOf("MDI"))
    whenever(prisonService.getCalculablePrisonerByPrison("MDI")).thenThrow(IllegalArgumentException("bug"))

    sender.processFullCaseLoadComparison(1, "token")

    verify(completer).handleErrorInBulkSetup(eq(1L), any())
  }

  @Test
  fun `when every eligible prison fails the run is an error, not an empty success`() {
    givenEligible(eligible = listOf("MDI", "LEI"))
    whenever(prisonService.getCalculablePrisonerByPrison(any())).thenThrow(WebClientResponseException(404, "Not found", null, null, null))

    sender.processFullCaseLoadComparison(1, "token")

    verify(completer).handleErrorInBulkSetup(eq(1L), any())
    verify(completer, never()).completeSetup(any(), any(), any())
  }

  @Test
  fun `no eligible prisons completes with zero people rather than erroring`() {
    givenEligible(eligible = emptyList(), inactive = listOf("DAI"))

    sender.processFullCaseLoadComparison(1, "token")

    verify(completer).completeSetup(1, 0, emptyList())
  }

  @Test
  fun `the single prison path still errors on any failure`() {
    whenever(prisonService.getCalculablePrisonerByPrison("DAI")).thenThrow(WebClientResponseException(404, "Not found", null, null, null))

    sender.processPrisonComparison(1, "DAI", "token")

    verify(completer).handleErrorInBulkSetup(eq(1L), any())
  }

  private fun givenEligible(eligible: List<String>, inactive: List<String> = emptyList()) {
    whenever(eligiblePrisonsService.eligibleCaseloadPrisons()).thenReturn(
      EligiblePrisons(caseload = eligible + inactive, eligible = eligible, inactive = inactive, registerAvailable = true),
    )
  }

  private fun prisoner(number: String) = CalculablePrisoner(prisonerNumber = number, bookingId = 1)
}
