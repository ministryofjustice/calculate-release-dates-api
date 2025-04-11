package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ToDoType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AnalysedBookingAndSentenceAdjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AnalysedSentenceAndOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SDSEarlyReleaseExclusionType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceAndOffenceAnalysis
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ThingsToDo
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonerDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceTerms
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.SentenceAndOffenceServiceTest.Companion.offences
import java.time.LocalDate

class ThingsToDoServiceTest {
  private val sentenceAndOffenceService = mock<SentenceAndOffenceService>()
  private val adjustmentsService = mock<AdjustmentsService>()
  private val prisonService = mock<PrisonService>()

  private val thingsToDoService = ThingsToDoService(sentenceAndOffenceService, adjustmentsService, prisonService)

  @Nested
  inner class GetToDoListTests {

    @Test
    fun `Get things to do for a prisoner where there are things to do`() {
      whenever(prisonService.getOffenderDetail(NOMS_ID)).thenReturn(PRISONER_DETAILS)
      whenever(sentenceAndOffenceService.getSentencesAndOffences(BOOKING_ID)).thenReturn(SENTENCES)
      whenever(adjustmentsService.getAnalysedBookingAndSentenceAdjustments(BOOKING_ID)).thenReturn(ADJUSTMENTS)

      val thingsToDo = thingsToDoService.getToDoList(NOMS_ID)

      assertThat(thingsToDo).isEqualTo(ThingsToDo(prisonerId = NOMS_ID, thingsToDo = listOf(ToDoType.CALCULATION_REQUIRED)))
    }

    @Test
    fun `Get things to do for a prisoner when there is nothing to do`() {
      whenever(prisonService.getOffenderDetail(NOMS_ID)).thenReturn(PRISONER_DETAILS)
      whenever(sentenceAndOffenceService.getSentencesAndOffences(BOOKING_ID)).thenReturn(listOf(BASE_SENTENCE.copy(sentenceAndOffenceAnalysis = SentenceAndOffenceAnalysis.SAME)))
      whenever(adjustmentsService.getAnalysedBookingAndSentenceAdjustments(BOOKING_ID)).thenReturn(ADJUSTMENTS)

      val thingsToDo = thingsToDoService.getToDoList(NOMS_ID)

      assertThat(thingsToDo).isEqualTo(ThingsToDo(prisonerId = NOMS_ID))
    }
  }

  companion object {
    const val NOMS_ID = "AA1234A"
    const val BOOKING_ID = 1234L
    private val PRISONER_DETAILS = PrisonerDetails(
      bookingId = BOOKING_ID,
      offenderNo = NOMS_ID,
      firstName = "John",
      lastName = "Smith",
      dateOfBirth = LocalDate.of(1970, 1, 1),
    )
    private val FIRST_JAN_2015: LocalDate = LocalDate.of(2015, 1, 1)
    private val BASE_SENTENCE = AnalysedSentenceAndOffence(
      bookingId = 1,
      sentenceSequence = 1,
      sentenceDate = FIRST_JAN_2015,
      terms = listOf(
        SentenceTerms(
          years = 5,
          months = 4,
          weeks = 3,
          days = 2,
        ),
      ),
      sentenceStatus = "IMP",
      sentenceCategory = "CAT",
      sentenceCalculationType = SentenceCalculationType.ADIMP.name,
      sentenceTypeDescription = "Standard Determinate",
      offence = offences[0],
      lineSequence = 1,
      caseSequence = 1,
      caseReference = null,
      fineAmount = null,
      courtDescription = null,
      consecutiveToSequence = null,
      isSDSPlus = false,
      hasAnSDSEarlyReleaseExclusion = SDSEarlyReleaseExclusionType.NO,
      sentenceAndOffenceAnalysis = SentenceAndOffenceAnalysis.NEW,
    )
    val SENTENCES = listOf(BASE_SENTENCE)

    val ADJUSTMENTS = AnalysedBookingAndSentenceAdjustments(
      bookingAdjustments = emptyList(),
      sentenceAdjustments = emptyList(),
    )
  }
}
