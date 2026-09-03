package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceAndOffenceWithReleaseArrangements
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.OffenderOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.nomissyncmapping.model.NomisDpsSentenceMapping
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.nomissyncmapping.model.NomisSentenceId
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.remandandsentencing.model.Sentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationMessage
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationUtilities
import java.time.LocalDate
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class DpsSentenceReferenceDecoratorServiceTest {

  private val nomisSyncMappingApiClient: NomisSyncMappingApiClient = mock()
  private val remandAndSentencingApiClient: RemandAndSentencingApiClient = mock()
  private val validationUtilities = ValidationUtilities()

  private val dpsSentenceReferenceDecoratorService = DpsSentenceReferenceDecoratorService(nomisSyncMappingApiClient, remandAndSentencingApiClient, validationUtilities)

  private val sentenceAndOffence = SentenceAndOffenceWithReleaseArrangements(
    bookingId = BOOKING_ID,
    sentenceSequence = SENTENCE_SEQUENCE,
    lineSequence = LINE_SEQUENCE,
    caseSequence = CASE_SEQUENCE,
    consecutiveToSequence = null,
    sentenceStatus = "A",
    sentenceCategory = "2003",
    sentenceCalculationType = "ADIMP",
    sentenceTypeDescription = "ADIMP",
    sentenceDate = LocalDate.of(2026, 3, 12),
    terms = emptyList(),
    offence = OffenderOffence(
      offenderChargeId = 1,
      offenceStartDate = LocalDate.of(2026, 1, 1),
      offenceCode = "OF123",
      offenceDescription = "Some offence",
    ),
    caseReference = "CASE123",
    courtId = "COURT1",
    courtDescription = "Birmingham Crown Court",
    courtTypeCode = null,
    fineAmount = null,
  )

  private val message = ValidationMessage(
    code = ValidationCode.OFFENCE_MISSING_DATE,
    arguments = listOf(CASE_SEQUENCE.toString(), LINE_SEQUENCE.toString()),
  )

  @Test
  fun `should return the messages unchanged when there are no critical messages`() {
    val result = dpsSentenceReferenceDecoratorService.decorateCriticalMessages(emptyList(), listOf(sentenceAndOffence))

    assertThat(result).isEmpty()
  }

  @Test
  fun `should build a dpsMessage using RAS count and NOMIS-sourced sentence data`() {
    val dpsSentenceId = UUID.randomUUID()
    whenever(nomisSyncMappingApiClient.postNomisToDpsMappingLookup(any())).thenReturn(
      listOf(NomisDpsSentenceMapping(NomisSentenceId(BOOKING_ID, SENTENCE_SEQUENCE), dpsSentenceId.toString())),
    )
    whenever(remandAndSentencingApiClient.getSentence(dpsSentenceId)).thenReturn(
      Sentence(sentenceUuid = dpsSentenceId, periodLengths = emptyList(), sentenceServeType = "CONCURRENT", hasRecall = false, chargeNumber = "2"),
    )

    val result = dpsSentenceReferenceDecoratorService.decorateCriticalMessages(listOf(message), listOf(sentenceAndOffence))

    assertThat(result).hasSize(1)
    assertThat(result.first().message).isEqualTo("Court case 3 NOMIS line reference 2 must include an offence date.")
    assertThat(result.first().dpsMessage).isEqualTo("Count 2 on case CASE123 at Birmingham Crown Court on 12 March 2026 must include an offence date.")
  }

  @Test
  fun `should fallback to a count-less message when the NOMIS mapping lookup fails`() {
    whenever(nomisSyncMappingApiClient.postNomisToDpsMappingLookup(any())).thenThrow(RuntimeException("connection refused"))

    val result = dpsSentenceReferenceDecoratorService.decorateCriticalMessages(listOf(message), listOf(sentenceAndOffence))

    assertThat(result).hasSize(1)
    assertThat(result.first().message).isEqualTo("Court case 3 NOMIS line reference 2 must include an offence date.")
    assertThat(result.first().dpsMessage)
      .isEqualTo("Offence (OF123 Some offence) committed on 1 January 2026 on case CASE123 at Birmingham Crown Court on 12 March 2026 must include an offence date.")
  }

  @Test
  fun `should fallback to a count-less message when the RAS lookup fails`() {
    val dpsSentenceId = UUID.randomUUID()
    whenever(nomisSyncMappingApiClient.postNomisToDpsMappingLookup(any())).thenReturn(
      listOf(NomisDpsSentenceMapping(NomisSentenceId(BOOKING_ID, SENTENCE_SEQUENCE), dpsSentenceId.toString())),
    )
    whenever(remandAndSentencingApiClient.getSentence(dpsSentenceId)).thenThrow(RuntimeException("not found"))

    val result = dpsSentenceReferenceDecoratorService.decorateCriticalMessages(listOf(message), listOf(sentenceAndOffence))

    assertThat(result).hasSize(1)
    assertThat(result.first().message).isEqualTo("Court case 3 NOMIS line reference 2 must include an offence date.")
    assertThat(result.first().dpsMessage)
      .isEqualTo("Offence (OF123 Some offence) committed on 1 January 2026 on case CASE123 at Birmingham Crown Court on 12 March 2026 must include an offence date.")
  }

  @Test
  fun `should return the message unchanged when no matching sentence can be found`() {
    val unmatched = message.copy(arguments = listOf("999", "999"))

    val result = dpsSentenceReferenceDecoratorService.decorateCriticalMessages(listOf(unmatched), listOf(sentenceAndOffence))

    assertThat(result).containsExactly(unmatched)
    assertThat(result.first().message).isEqualTo("Court case 3 NOMIS line reference 2 must include an offence date.")
    assertThat(result.first().dpsMessage).isEqualTo("3 must include an offence date.")
  }

  @Test
  fun `should only look up RAS once per distinct sentence, even if multiple messages reference it`() {
    val dpsSentenceId = UUID.randomUUID()
    whenever(nomisSyncMappingApiClient.postNomisToDpsMappingLookup(any())).thenReturn(
      listOf(NomisDpsSentenceMapping(NomisSentenceId(BOOKING_ID, SENTENCE_SEQUENCE), dpsSentenceId.toString())),
    )
    whenever(remandAndSentencingApiClient.getSentence(dpsSentenceId)).thenReturn(
      Sentence(sentenceUuid = dpsSentenceId, periodLengths = emptyList(), sentenceServeType = "CONCURRENT", hasRecall = false, chargeNumber = "2"),
    )

    val anotherMessage = ValidationMessage(
      code = ValidationCode.SENTENCE_HAS_NO_IMPRISONMENT_TERM,
      arguments = listOf(CASE_SEQUENCE.toString(), LINE_SEQUENCE.toString()),
    )

    dpsSentenceReferenceDecoratorService.decorateCriticalMessages(listOf(message, anotherMessage), listOf(sentenceAndOffence))

    verify(nomisSyncMappingApiClient, times(1)).postNomisToDpsMappingLookup(eq(listOf(NomisSentenceId(BOOKING_ID, SENTENCE_SEQUENCE))))
    verify(remandAndSentencingApiClient, times(1)).getSentence(dpsSentenceId)
  }

  companion object {
    private const val BOOKING_ID = 1234L
    private const val SENTENCE_SEQUENCE = 1
    private const val LINE_SEQUENCE = 2
    private const val CASE_SEQUENCE = 3
  }
}
