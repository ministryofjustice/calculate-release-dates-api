package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.eq
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceAndOffenceAnalysis
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceAndOffencesWithReleaseArrangements
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.OffenderOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonApiSentenceAndOffences
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceTerms
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationRequestRepository
import java.time.LocalDate
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class SentenceAndOffenceServiceTest {

  @Mock
  lateinit var prisonService: PrisonService

  @Mock
  lateinit var calculationRequestRepository: CalculationRequestRepository

  @Mock
  lateinit var prisonApiDataMapper: PrisonApiDataMapper

  @InjectMocks
  lateinit var underTest: SentenceAndOffenceService

  @Test
  fun `If no previous calculation return sentenceAndOffences`() {
    whenever(prisonService.getSentencesAndOffences(anyLong(), eq(true))).thenReturn(listOf(sentenceAndOffences))
    whenever(calculationRequestRepository.findLatestCalculation(anyLong())).thenReturn(Optional.empty())
    val response = underTest.getSentencesAndOffences(123)
    assertThat(response).hasSize(1)
    assertThat(response.get(0).sentenceAndOffenceAnalysis).isEqualTo(SentenceAndOffenceAnalysis.NEW)
  }

  @Test
  fun `If no change since previous calculation return sentenceAndOffences with SAME annotation`() {
    whenever(prisonService.getSentencesAndOffences(anyLong(), eq(true))).thenReturn(listOf(sentenceAndOffences))
    whenever(calculationRequestRepository.findLatestCalculation(anyLong())).thenReturn(Optional.of(calculationRequest))
    whenever(prisonApiDataMapper.mapSentencesAndOffences(calculationRequest)).thenReturn(listOf(sentenceAndOffences))
    val response = underTest.getSentencesAndOffences(123)
    assertThat(response).hasSize(1)
    assertThat(response.get(0).sentenceAndOffenceAnalysis).isEqualTo(SentenceAndOffenceAnalysis.SAME)
  }

  @Test
  fun `If old version did not have SDS plus flag (defaults to false) ignore the difference`() {
    whenever(prisonService.getSentencesAndOffences(anyLong(), eq(true))).thenReturn(listOf(sentenceAndOffences.copy(isSdsPlus = true)))
    val defaultedSentencesAndOffences = sentenceAndOffences.copy(isSdsPlus = false)
    val calcRequestWithMissingSDSPlusFlag = CalculationRequest(sentenceAndOffences = objectToJson(listOf(defaultedSentencesAndOffences), jacksonObjectMapper().findAndRegisterModules()))
    whenever(prisonApiDataMapper.mapSentencesAndOffences(calculationRequest)).thenReturn(listOf(defaultedSentencesAndOffences))
    whenever(calculationRequestRepository.findLatestCalculation(anyLong())).thenReturn(Optional.of(calcRequestWithMissingSDSPlusFlag))
    val response = underTest.getSentencesAndOffences(123)
    assertThat(response).hasSize(1)
    assertThat(response.get(0).sentenceAndOffenceAnalysis).isEqualTo(SentenceAndOffenceAnalysis.SAME)
  }

  @Test
  fun `If offence change since previous calculation return sentenceAndOffences with CHANGE annotation`() {
    whenever(prisonService.getSentencesAndOffences(anyLong(), eq(true))).thenReturn(listOf(sentenceAndOffences))
    whenever(calculationRequestRepository.findLatestCalculation(anyLong())).thenReturn(Optional.of(changedCalculationRequest))
    whenever(prisonApiDataMapper.mapSentencesAndOffences(changedCalculationRequest)).thenReturn(listOf(changedSentenceAndOffences))
    val response = underTest.getSentencesAndOffences(123)
    assertThat(response).hasSize(1)
    assertThat(response.get(0).sentenceAndOffenceAnalysis).isEqualTo(SentenceAndOffenceAnalysis.UPDATED)
  }

  @Test
  fun `If a new sentence appears since previous calculation return sentenceAndOffences with SAME and NEW annotation`() {
    whenever(prisonService.getSentencesAndOffences(anyLong(), eq(true))).thenReturn(listOf(sentenceAndOffences, newSentenceAndOffences))
    whenever(calculationRequestRepository.findLatestCalculation(anyLong())).thenReturn(Optional.of(calculationRequest))
    whenever(prisonApiDataMapper.mapSentencesAndOffences(calculationRequest)).thenReturn(listOf(sentenceAndOffences))
    val response = underTest.getSentencesAndOffences(123)
    assertThat(response).hasSize(2)
    assertThat(response.get(0).sentenceAndOffenceAnalysis).isEqualTo(SentenceAndOffenceAnalysis.SAME)
    assertThat(response.get(1).sentenceAndOffenceAnalysis).isEqualTo(SentenceAndOffenceAnalysis.NEW)
  }
  companion object {
    private val FIRST_JAN_2015: LocalDate = LocalDate.of(2015, 1, 1)
    private val SECOND_JAN_2015: LocalDate = LocalDate.of(2015, 1, 2)
    private val bookingId = 1110022L
    private val lineSequence = 154
    private val caseSequence = 155
    val offences = listOf(
      OffenderOffence(
        offenderChargeId = bookingId,
        offenceStartDate = FIRST_JAN_2015,
        offenceCode = "RR1",
        offenceDescription = "Littering",
        indicators = listOf("An indicator"),
      ),
      OffenderOffence(
        offenderChargeId = 2L,
        offenceStartDate = SECOND_JAN_2015,
        offenceCode = "RR2",
        offenceDescription = "Jaywalking",
        indicators = listOf(OffenderOffence.SCHEDULE_15_LIFE_INDICATOR),
      ),
    )
    val changedOffences = listOf(
      OffenderOffence(
        offenderChargeId = bookingId,
        offenceStartDate = FIRST_JAN_2015,
        offenceCode = "RR1",
        offenceDescription = "Littering",
        indicators = listOf("An indicator"),
      ),
      OffenderOffence(
        offenderChargeId = 2L,
        offenceStartDate = SECOND_JAN_2015,
        offenceCode = "RR3",
        offenceDescription = "Jaywalking",
        indicators = listOf(OffenderOffence.SCHEDULE_15_LIFE_INDICATOR),
      ),
    )
    val newOffences = listOf(
      OffenderOffence(
        offenderChargeId = bookingId,
        offenceStartDate = FIRST_JAN_2015,
        offenceCode = "RR4",
        offenceDescription = "Littering",
        indicators = listOf("An indicator"),
      ),
      OffenderOffence(
        offenderChargeId = 2L,
        offenceStartDate = SECOND_JAN_2015,
        offenceCode = "RR5",
        offenceDescription = "Jaywalking",
        indicators = listOf(OffenderOffence.SCHEDULE_15_LIFE_INDICATOR),
      ),
    )
    val sentenceAndOffences = SentenceAndOffencesWithReleaseArrangements(
      PrisonApiSentenceAndOffences(
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
        offences = offences,
        lineSequence = lineSequence,
        caseSequence = caseSequence,
      ),
      isSdsPlus = false,
    )
    val changedSentenceAndOffences = SentenceAndOffencesWithReleaseArrangements(
      PrisonApiSentenceAndOffences(
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
        offences = changedOffences,
        lineSequence = lineSequence,
        caseSequence = caseSequence,
      ),
      isSdsPlus = true,
    )
    val newSentenceAndOffences = SentenceAndOffencesWithReleaseArrangements(
      PrisonApiSentenceAndOffences(
        bookingId = 1,
        sentenceSequence = 2,
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
        offences = newOffences,
        lineSequence = lineSequence,
        caseSequence = caseSequence,
      ),
      isSdsPlus = true,
    )
    val calculationRequest = CalculationRequest(sentenceAndOffences = objectToJson(listOf(sentenceAndOffences), jacksonObjectMapper().findAndRegisterModules()))
    val changedCalculationRequest = CalculationRequest(sentenceAndOffences = objectToJson(listOf(changedSentenceAndOffences), jacksonObjectMapper().findAndRegisterModules()))
  }
}
