package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import arrow.core.right
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Agency
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.NormalisedSentenceAndOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.OffenderKeyDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RestResponsePage
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceAndOffenceWithReleaseArrangements
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceCalculationSummary
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.OffenderOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonApiSentenceAndOffences
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi.CalculableSentenceEnvelope
import java.time.LocalDate
import java.time.LocalDateTime

class PrisonServiceTest {
  private val prisonApiClient = mock<PrisonApiClient>()
  private val offenceSdsPlusLookupService = mock<OffenceSdsPlusLookupService>()
  private val prisonService = PrisonService(prisonApiClient, offenceSdsPlusLookupService)

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
    val expected = OffenderKeyDates(reasonCode = "NEW", calculatedAt = LocalDateTime.now())
    whenever(prisonApiClient.getOffenderKeyDates(bookingId)).thenReturn(expected.right())
    val keyDates = prisonService.getOffenderKeyDates(bookingId)
    assertThat(keyDates).isEqualTo(expected.right())
  }

  @Test
  fun `should get offender key dates by using offender sent calc id`() {
    val offenderSentCalcId = 123456L
    val expected = OffenderKeyDates(reasonCode = "NEW", calculatedAt = LocalDateTime.now())
    whenever(prisonApiClient.getNOMISOffenderKeyDates(offenderSentCalcId)).thenReturn(expected.right())
    val keyDates = prisonService.getNOMISOffenderKeyDates(offenderSentCalcId)
    assertThat(keyDates).isEqualTo(expected.right())
  }

  @Test
  fun `should normalise sentence and offences`() {
    val offence1 = OffenderOffence(1L, LocalDate.of(2015, 1, 1), null, "ADIMP", "description", listOf("A"))
    val offence2 = OffenderOffence(2L, LocalDate.of(2015, 2, 2), null, "ADIMP", "description", listOf("A"))
    val prisonApiSentencesAndOffences = listOf(
      PrisonApiSentenceAndOffences(
        1,
        1,
        1,
        1,
        null,
        "A",
        "A",
        "LIFE",
        "",
        LocalDate.now(),
        offences = listOf(offence1, offence2),
      ),
    )
    val normalisedOffences = listOf(
      NormalisedSentenceAndOffence(
        1,
        1,
        1,
        1,
        null,
        "A",
        "A",
        "LIFE",
        "",
        LocalDate.now(),
        offence = offence1,
        caseReference = null,
        courtDescription = null,
        fineAmount = null,
        terms = emptyList(),
      ),
      NormalisedSentenceAndOffence(
        1,
        1,
        1,
        1,
        null,
        "A",
        "A",
        "LIFE",
        "",
        LocalDate.now(),
        offence = offence2,
        caseReference = null,
        courtDescription = null,
        fineAmount = null,
        terms = emptyList(),
      ),

    )

    val withReleaseArrangements = normalisedOffences.map { SentenceAndOffenceWithReleaseArrangements(it, false) }
    whenever(prisonApiClient.getSentencesAndOffences(1)).thenReturn(prisonApiSentencesAndOffences)
    whenever(offenceSdsPlusLookupService.populateSdsPlusMarkerForOffences(normalisedOffences)).thenReturn(withReleaseArrangements)
    assertThat(prisonService.getSentencesAndOffences(1, true)).isEqualTo(withReleaseArrangements)
  }

  @Test
  fun `should include only active sentences if requested`() {
    val offence1 = OffenderOffence(1L, LocalDate.of(2015, 1, 1), null, "ADIMP", "description", listOf("A"))
    val offence2 = OffenderOffence(2L, LocalDate.of(2015, 2, 2), null, "ADIMP", "description", listOf("NOTA"))
    val prisonApiSentenceAndOffences1 = PrisonApiSentenceAndOffences(
      1,
      1,
      1,
      1,
      null,
      "A",
      "A",
      "LIFE",
      "",
      LocalDate.now(),
      offences = listOf(offence1),
    )
    val prisonApiSentenceAndOffences2 = PrisonApiSentenceAndOffences(
      1,
      1,
      1,
      1,
      null,
      "NOTA",
      "A",
      "LIFE",
      "",
      LocalDate.now(),
      offences = listOf(offence2),
    )

    val activeOffence = NormalisedSentenceAndOffence(prisonApiSentenceAndOffences1, offence1)

    whenever(prisonApiClient.getSentencesAndOffences(1)).thenReturn(listOf(prisonApiSentenceAndOffences1, prisonApiSentenceAndOffences2))
    whenever(offenceSdsPlusLookupService.populateSdsPlusMarkerForOffences(listOf(activeOffence))).thenReturn(listOf(SentenceAndOffenceWithReleaseArrangements(activeOffence, false)))
    assertThat(prisonService.getSentencesAndOffences(1, true)).isEqualTo(listOf(SentenceAndOffenceWithReleaseArrangements(activeOffence, false)))
  }

  @Test
  fun `should not filter inactive sentences if requested`() {
    val offence1 = OffenderOffence(1L, LocalDate.of(2015, 1, 1), null, "ADIMP", "description", listOf("A"))
    val offence2 = OffenderOffence(2L, LocalDate.of(2015, 2, 2), null, "ADIMP", "description", listOf("NOTA"))
    val prisonApiSentenceAndOffences1 = PrisonApiSentenceAndOffences(
      1,
      1,
      1,
      1,
      null,
      "A",
      "A",
      "LIFE",
      "",
      LocalDate.now(),
      offences = listOf(offence1),
    )
    val prisonApiSentenceAndOffences2 = PrisonApiSentenceAndOffences(
      1,
      1,
      1,
      1,
      null,
      "NOTA",
      "A",
      "LIFE",
      "",
      LocalDate.now(),
      offences = listOf(offence2),
    )

    val activeOffence = NormalisedSentenceAndOffence(prisonApiSentenceAndOffences1, offence1)
    val inactiveOffence = NormalisedSentenceAndOffence(prisonApiSentenceAndOffences2, offence2)

    whenever(prisonApiClient.getSentencesAndOffences(1)).thenReturn(listOf(prisonApiSentenceAndOffences1, prisonApiSentenceAndOffences2))
    whenever(offenceSdsPlusLookupService.populateSdsPlusMarkerForOffences(listOf(activeOffence, inactiveOffence))).thenReturn(listOf(SentenceAndOffenceWithReleaseArrangements(activeOffence, false), SentenceAndOffenceWithReleaseArrangements(inactiveOffence, false)))
    assertThat(prisonService.getSentencesAndOffences(1, false)).isEqualTo(listOf(SentenceAndOffenceWithReleaseArrangements(activeOffence, false), SentenceAndOffenceWithReleaseArrangements(inactiveOffence, false)))
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
