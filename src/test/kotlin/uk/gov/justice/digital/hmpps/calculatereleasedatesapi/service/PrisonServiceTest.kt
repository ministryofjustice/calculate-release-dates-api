package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Agency
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.OffenderKeyDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RestResponsePage
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceCalculationSummary
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi.CalculableSentenceEnvelope
import java.time.LocalDateTime

class PrisonServiceTest {
  private val prisonApiClient = mock<PrisonApiClient>()
  private val prisonService = PrisonService(prisonApiClient)

  @Test
  fun `The request to fetch Calculable Sentences is sent once per page until the last page is retrieved`() {
    whenever(prisonApiClient.getCalculableSentenceEnvelopesByEstablishment("LEI", 0, "")).thenReturn(firstPage)
    whenever(prisonApiClient.getCalculableSentenceEnvelopesByEstablishment("LEI", 1, "")).thenReturn(secondPage)

    prisonService.getActiveBookingsByEstablishment("LEI", "")

    verify(prisonApiClient).getCalculableSentenceEnvelopesByEstablishment("LEI", 0, "")
    verify(prisonApiClient).getCalculableSentenceEnvelopesByEstablishment("LEI", 1, "")
    verifyNoMoreInteractions(prisonApiClient)
  }

  @Test
  fun `should get calculation history for an offender`() {
    val prisonId = "G0127UG"
    val agencyDescription = "Cookham Wood (HMP)"
    val sentenceCalculationSummary = SentenceCalculationSummary(47, prisonId, "first name", "last name", "CKI", agencyDescription, 28, LocalDateTime.now(), 4, "comment", "Lodged warrant", "user")
    whenever(prisonApiClient.getCalculationsForAPrisonerId(prisonId)).thenReturn(listOf(sentenceCalculationSummary))

    val history = prisonService.getCalculationsForAPrisonerId(prisonId)

    assertThat(history).isNotNull()
    assertThat(history).hasSize(1)
    assertThat(history[0].offenderNo).isEqualTo(prisonId)
    assertThat(history[0].agencyDescription).isEqualTo(agencyDescription)
  }

  @Test
  fun `should get agencies by type`() {
    val prisonApiAgencies = listOf(Agency("LWI", "Lewes (HMP)"), Agency("RSI", "Risley (HMP)"))
    whenever(prisonApiClient.getAgenciesByType("INST")).thenReturn(prisonApiAgencies)
    val returnedAgencies = prisonService.getAgenciesByType("INST")

    assertThat(returnedAgencies).isEqualTo(prisonApiAgencies)
  }

  @Test
  fun `should get offender key dates`() {
    val bookingId = 123456L
    val expected = OffenderKeyDates()
    whenever(prisonApiClient.getOffenderKeyDates(bookingId)).thenReturn(expected)
    val keyDates = prisonService.getOffenderKeyDates(bookingId)
    assertThat(keyDates).isEqualTo(expected)
  }

  companion object {
    private val mapper = ObjectMapper()

    private const val FIRST_PAGE_PAGEABLE = """
       "pageable": {
          "pageNumber": 0,
          "pageSize": 1,
          "sort": {
            "empty": false,
            "sorted": true,
            "unsorted": false
          },
          "offset": 0,
          "paged": true,
          "unpaged": false
        }
    """
    private const val SECOND_PAGE_PAGEABLE = """
       "pageable": {
          "pageNumber": 1,
          "pageSize": 1,
          "sort": {
            "empty": false,
            "sorted": true,
            "unsorted": false
          },
          "offset": 0,
          "paged": true,
          "unpaged": false
        }
    """

    val firstPage = RestResponsePage<CalculableSentenceEnvelope>(
      content = emptyList(),
      pageable = mapper.readTree(FIRST_PAGE_PAGEABLE),
      totalElements = 2,
      size = 1,
      number = 0,
    )

    val secondPage = RestResponsePage<CalculableSentenceEnvelope>(
      content = emptyList(),
      pageable = mapper.readTree(SECOND_PAGE_PAGEABLE),
      totalElements = 2,
      size = 1,
      number = 1,
    )
  }
}
