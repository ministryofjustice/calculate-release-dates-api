package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RestResponsePage
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi.CalculableSentenceEnvelope

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
