package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
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
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.SentenceIdentifier
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationUtilities
import java.time.LocalDate
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class DpsValidationMessageDecoratorServiceTest {

  private val nomisSyncMappingApiClient: NomisSyncMappingApiClient = mock()
  private val remandAndSentencingApiClient: RemandAndSentencingApiClient = mock()
  private val validationUtilities = ValidationUtilities()

  private val dpsValidationMessageDecoratorService =
    DpsValidationMessageDecoratorService(nomisSyncMappingApiClient, remandAndSentencingApiClient)

  private val message = validationUtilities.createValidationMessage(ValidationCode.OFFENCE_MISSING_DATE, sentenceAndOffence())

  @Nested
  inner class DpsMessageFormatting {

    @Test
    fun `AC1 - count number only`() {
      mockCount(2)
      decorateAndAssert(
        sentenceAndOffence(),
        "Court case 3 NOMIS line reference 2 must include an offence date.",
        "Count 2 on case Birmingham Crown Court on 12/03/2026 must include an offence date.",
      )
    }

    @Test
    fun `AC2 - count and offence date (offence date ignored)`() {
      mockCount(2)
      decorateAndAssert(
        sentenceAndOffence(offenceDate = LocalDate.of(2026, 1, 1)),
        "Court case 3 NOMIS line reference 2 must include an offence date.",
        "Count 2 on case Birmingham Crown Court on 12/03/2026 must include an offence date.",
      )
    }

    @Test
    fun `AC3 - count and case reference`() {
      mockCount(2)
      decorateAndAssert(
        sentenceAndOffence(caseReference = "CASE123"),
        "Court case 3 NOMIS line reference 2 must include an offence date.",
        "Count 2 on case CASE123 at Birmingham Crown Court on 12/03/2026 must include an offence date.",
      )
    }

    @Test
    fun `AC4 - offence date and case reference`() {
      mockCount(null)
      decorateAndAssert(
        sentenceAndOffence(offenceDate = LocalDate.of(2026, 1, 1), caseReference = "CASE123"),
        "Court case 3 NOMIS line reference 2 must include an offence date.",
        "OF123 - Some offence committed on 01/01/2026 on case CASE123 at Birmingham Crown Court on 12/03/2026 must include an offence date.",
      )
    }

    @Test
    fun `AC5 - offence date only`() {
      mockCount(null)
      decorateAndAssert(
        sentenceAndOffence(offenceDate = LocalDate.of(2026, 1, 1)),
        "Court case 3 NOMIS line reference 2 must include an offence date.",
        "OF123 - Some offence committed on 01/01/2026 at Birmingham Crown Court on 12/03/2026 must include an offence date.",
      )
    }

    @Test
    fun `AC6 - case reference only`() {
      mockCount(null)
      decorateAndAssert(
        sentenceAndOffence(caseReference = "CASE123"),
        "Court case 3 NOMIS line reference 2 must include an offence date.",
        "OF123 - Some offence on case CASE123 at Birmingham Crown Court on 12/03/2026 must include an offence date.",
      )
    }

    @Test
    fun `AC7 - count, case reference and offence date (offence date ignored)`() {
      mockCount(2)
      decorateAndAssert(
        sentenceAndOffence(offenceDate = LocalDate.of(2026, 1, 1), caseReference = "CASE123"),
        "Court case 3 NOMIS line reference 2 must include an offence date.",
        "Count 2 on case CASE123 at Birmingham Crown Court on 12/03/2026 must include an offence date.",
      )
    }

    @Test
    fun `default - none of the optional fields present`() {
      mockCount(null)
      decorateAndAssert(
        sentenceAndOffence(),
        "Court case 3 NOMIS line reference 2 must include an offence date.",
        "OF123 - Some offence at Birmingham Crown Court on 12/03/2026 must include an offence date.",
      )
    }
  }

  @Nested
  inner class LookupBehaviour {

    @Test
    fun `should return the messages unchanged when there are no critical messages`() {
      val result = dpsValidationMessageDecoratorService.decorateCriticalMessages(emptyList(), listOf(sentenceAndOffence()))

      assertThat(result).isEmpty()
    }

    @Test
    fun `should fallback to a count-less message when the NOMIS mapping lookup fails`() {
      whenever(nomisSyncMappingApiClient.postNomisToDpsMappingLookup(any()))
        .thenThrow(RuntimeException("connection refused"))

      decorateAndAssert(
        sentenceAndOffence(offenceDate = LocalDate.of(2026, 1, 1), caseReference = "CASE123"),
        "Court case 3 NOMIS line reference 2 must include an offence date.",
        "OF123 - Some offence committed on 01/01/2026 on case CASE123 at Birmingham Crown Court on 12/03/2026 must include an offence date.",
      )
    }

    @Test
    fun `should fallback to a count-less message when the RAS lookup fails`() {
      val dpsSentenceId = UUID.randomUUID()
      whenever(nomisSyncMappingApiClient.postNomisToDpsMappingLookup(any())).thenReturn(
        listOf(NomisDpsSentenceMapping(NomisSentenceId(BOOKING_ID, SENTENCE_SEQUENCE), dpsSentenceId.toString())),
      )
      whenever(remandAndSentencingApiClient.getSentence(dpsSentenceId)).thenThrow(RuntimeException("not found"))

      decorateAndAssert(
        sentenceAndOffence(offenceDate = LocalDate.of(2026, 1, 1), caseReference = "CASE123"),
        "Court case 3 NOMIS line reference 2 must include an offence date.",
        "OF123 - Some offence committed on 01/01/2026 on case CASE123 at Birmingham Crown Court on 12/03/2026 must include an offence date.",
      )
    }

    @Test
    fun `should return the message unchanged when no matching sentence can be found`() {
      val unmatched = message.copy(sentenceIdentifier = SentenceIdentifier(bookingId = 9999L, sentenceSequence = 999))

      val result = dpsValidationMessageDecoratorService.decorateCriticalMessages(listOf(unmatched), listOf(sentenceAndOffence()))

      assertThat(result).containsExactly(unmatched)
      assertThat(result.first().message).isEqualTo("Court case 3 NOMIS line reference 2 must include an offence date.")
      assertThat(result.first().dpsMessage).isEqualTo("3 must include an offence date.")
    }

    @Test
    fun `should only look up RAS once per distinct sentence, even if multiple messages reference it`() {
      mockCount(2)
      val anotherMessage = validationUtilities.createValidationMessage(ValidationCode.SENTENCE_HAS_NO_IMPRISONMENT_TERM, sentenceAndOffence())

      val result = dpsValidationMessageDecoratorService.decorateCriticalMessages(listOf(message, anotherMessage), listOf(sentenceAndOffence()))

      assertThat(result.map { it.message }).containsExactly(
        "Court case 3 NOMIS line reference 2 must include an offence date.",
        "Court case 3 NOMIS line reference 2 must include an imprisonment term.",
      )
      verify(nomisSyncMappingApiClient, times(1)).postNomisToDpsMappingLookup(eq(listOf(NomisSentenceId(BOOKING_ID, SENTENCE_SEQUENCE))))
    }

    @Test
    fun `should fall back to courtId when courtDescription is null`() {
      mockCount(null)
      decorateAndAssert(
        sentenceAndOffence(courtDescription = null),
        "Court case 3 NOMIS line reference 2 must include an offence date.",
        "OF123 - Some offence at COURT1 on 12/03/2026 must include an offence date.",
      )
    }

    @Test
    fun `should fall back to 'the court' when courtDescription and courtId are both null`() {
      mockCount(null)
      decorateAndAssert(
        sentenceAndOffence(courtDescription = null, courtId = null),
        "Court case 3 NOMIS line reference 2 must include an offence date.",
        "OF123 - Some offence at the court on 12/03/2026 must include an offence date.",
      )
    }
  }

  private fun sentenceAndOffence(
    offenceDate: LocalDate? = null,
    caseReference: String? = null,
    courtId: String? = "COURT1",
    courtDescription: String? = "Birmingham Crown Court",
  ) = SentenceAndOffenceWithReleaseArrangements(
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
      offenceStartDate = offenceDate,
      offenceCode = "OF123",
      offenceDescription = "Some offence",
    ),
    caseReference = caseReference,
    courtId = courtId,
    courtDescription = courtDescription,
    courtTypeCode = null,
    fineAmount = null,
  )

  private fun mockCount(count: Int?) {
    if (count == null) {
      whenever(nomisSyncMappingApiClient.postNomisToDpsMappingLookup(any()))
        .thenThrow(RuntimeException("connection refused"))
    } else {
      val dpsSentenceId = UUID.randomUUID()
      whenever(nomisSyncMappingApiClient.postNomisToDpsMappingLookup(any())).thenReturn(
        listOf(NomisDpsSentenceMapping(NomisSentenceId(BOOKING_ID, SENTENCE_SEQUENCE), dpsSentenceId.toString())),
      )
      whenever(remandAndSentencingApiClient.getSentence(dpsSentenceId)).thenReturn(
        Sentence(
          sentenceUuid = dpsSentenceId,
          periodLengths = emptyList(),
          sentenceServeType = "CONCURRENT",
          hasRecall = false,
          chargeNumber = count.toString(),
        ),
      )
    }
  }

  private fun decorateAndAssert(
    sentenceAndOffence: SentenceAndOffenceWithReleaseArrangements,
    expectedNomisMessage: String,
    expectedDpsMessage: String,
  ) {
    val result = dpsValidationMessageDecoratorService.decorateCriticalMessages(listOf(message), listOf(sentenceAndOffence))

    assertThat(result).hasSize(1)
    assertThat(result.first().message).isEqualTo(expectedNomisMessage)
    assertThat(result.first().dpsMessage).isEqualTo(expectedDpsMessage)
  }

  companion object {
    private const val BOOKING_ID = 1234L
    private const val SENTENCE_SEQUENCE = 1
    private const val LINE_SEQUENCE = 2
    private const val CASE_SEQUENCE = 3
  }
}
