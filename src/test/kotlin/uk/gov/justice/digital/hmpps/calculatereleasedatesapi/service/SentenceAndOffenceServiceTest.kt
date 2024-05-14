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
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AnalysedSentenceAndOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SDSEarlyReleaseExclusionType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceAndOffenceAnalysis
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceAndOffenceWithReleaseArrangements
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.OffenderOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceTerms
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationRequestRepository
import java.time.LocalDate
import java.util.*

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
    whenever(prisonService.getSentencesAndOffences(anyLong(), eq(true))).thenReturn(sentenceAndOffences)
    whenever(calculationRequestRepository.findLatestCalculation(anyLong())).thenReturn(Optional.empty())
    val response = underTest.getSentencesAndOffences(123)
    assertThat(response).hasSize(2)
    val analysedSentenceAndOffence = AnalysedSentenceAndOffence(
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
      lineSequence = lineSequence,
      caseSequence = caseSequence,
      caseReference = null,
      fineAmount = null,
      courtDescription = null,
      consecutiveToSequence = null,
      isSDSPlus = false,
      hasAnSDSEarlyReleaseExclusion = SDSEarlyReleaseExclusionType.NO,
      sentenceAndOffenceAnalysis = SentenceAndOffenceAnalysis.NEW,
    )
    assertThat(response[0]).isEqualTo(analysedSentenceAndOffence)
    assertThat(response[1]).isEqualTo(analysedSentenceAndOffence.copy(offence = offences[1]))
  }

  @Test
  fun `If no change since previous calculation return sentenceAndOffences with SAME annotation`() {
    whenever(prisonService.getSentencesAndOffences(anyLong(), eq(true))).thenReturn(sentenceAndOffences)
    whenever(calculationRequestRepository.findLatestCalculation(anyLong())).thenReturn(Optional.of(calculationRequest))
    whenever(prisonApiDataMapper.mapSentencesAndOffences(calculationRequest)).thenReturn(sentenceAndOffences)
    val response = underTest.getSentencesAndOffences(123)
    assertThat(response).hasSize(2)
    assertThat(response[0].sentenceAndOffenceAnalysis).isEqualTo(SentenceAndOffenceAnalysis.SAME)
    assertThat(response[1].sentenceAndOffenceAnalysis).isEqualTo(SentenceAndOffenceAnalysis.SAME)
  }

  @Test
  fun `If old version did not have SDS plus flag (defaults to false) ignore the difference`() {
    whenever(prisonService.getSentencesAndOffences(anyLong(), eq(true))).thenReturn(sentenceAndOffences.map { it.copy(isSDSPlus = true) })
    val defaultedSentencesAndOffences = sentenceAndOffences.map { it.copy(isSDSPlus = false) }
    val calcRequestWithMissingSDSPlusFlag = CalculationRequest(sentenceAndOffences = objectToJson(defaultedSentencesAndOffences, jacksonObjectMapper().findAndRegisterModules()))
    whenever(prisonApiDataMapper.mapSentencesAndOffences(calculationRequest)).thenReturn(defaultedSentencesAndOffences)
    whenever(calculationRequestRepository.findLatestCalculation(anyLong())).thenReturn(Optional.of(calcRequestWithMissingSDSPlusFlag))
    val response = underTest.getSentencesAndOffences(123)
    assertThat(response).hasSize(2)
    assertThat(response[0].sentenceAndOffenceAnalysis).isEqualTo(SentenceAndOffenceAnalysis.SAME)
    assertThat(response[1].sentenceAndOffenceAnalysis).isEqualTo(SentenceAndOffenceAnalysis.SAME)
  }

  @Test
  fun `If offence change since previous calculation return sentenceAndOffences with CHANGE annotation`() {
    whenever(prisonService.getSentencesAndOffences(anyLong(), eq(true))).thenReturn(sentenceAndOffences)
    whenever(calculationRequestRepository.findLatestCalculation(anyLong())).thenReturn(Optional.of(changedCalculationRequest))
    whenever(prisonApiDataMapper.mapSentencesAndOffences(changedCalculationRequest)).thenReturn(changedSentenceAndOffences)
    val response = underTest.getSentencesAndOffences(123)
    assertThat(response).hasSize(2)
    assertThat(response[0].sentenceAndOffenceAnalysis).isEqualTo(SentenceAndOffenceAnalysis.UPDATED)
    assertThat(response[1].sentenceAndOffenceAnalysis).isEqualTo(SentenceAndOffenceAnalysis.UPDATED)
  }

  @Test
  fun `If a new sentence appears since previous calculation return sentenceAndOffences with SAME and NEW annotation`() {
    whenever(prisonService.getSentencesAndOffences(anyLong(), eq(true))).thenReturn(sentenceAndOffences + newSentenceAndOffences)
    whenever(calculationRequestRepository.findLatestCalculation(anyLong())).thenReturn(Optional.of(calculationRequest))
    whenever(prisonApiDataMapper.mapSentencesAndOffences(calculationRequest)).thenReturn(sentenceAndOffences)
    val response = underTest.getSentencesAndOffences(123)
    assertThat(response).hasSize(4)
    assertThat(response[0].sentenceAndOffenceAnalysis).isEqualTo(SentenceAndOffenceAnalysis.SAME)
    assertThat(response[1].sentenceAndOffenceAnalysis).isEqualTo(SentenceAndOffenceAnalysis.SAME)
    assertThat(response[2].sentenceAndOffenceAnalysis).isEqualTo(SentenceAndOffenceAnalysis.NEW)
    assertThat(response[3].sentenceAndOffenceAnalysis).isEqualTo(SentenceAndOffenceAnalysis.NEW)
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
      ),
      OffenderOffence(
        offenderChargeId = 2L,
        offenceStartDate = SECOND_JAN_2015,
        offenceCode = "RR2",
        offenceDescription = "Jaywalking",
      ),
    )
    val changedOffences = listOf(
      OffenderOffence(
        offenderChargeId = bookingId,
        offenceStartDate = FIRST_JAN_2015,
        offenceCode = "RR1",
        offenceDescription = "Littering",
      ),
      OffenderOffence(
        offenderChargeId = 2L,
        offenceStartDate = SECOND_JAN_2015,
        offenceCode = "RR3",
        offenceDescription = "Jaywalking",
      ),
    )
    val newOffences = listOf(
      OffenderOffence(
        offenderChargeId = bookingId,
        offenceStartDate = FIRST_JAN_2015,
        offenceCode = "RR4",
        offenceDescription = "Littering",
      ),
      OffenderOffence(
        offenderChargeId = 2L,
        offenceStartDate = SECOND_JAN_2015,
        offenceCode = "RR5",
        offenceDescription = "Jaywalking",
      ),
    )
    val sentenceAndOffences = offences.map {
      SentenceAndOffenceWithReleaseArrangements(
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
        offence = it,
        lineSequence = lineSequence,
        caseSequence = caseSequence,
        caseReference = null,
        fineAmount = null,
        courtDescription = null,
        consecutiveToSequence = null,
        isSDSPlus = false,
        hasAnSDSEarlyReleaseExclusion = SDSEarlyReleaseExclusionType.NO,
      )
    }
    val changedSentenceAndOffences = changedOffences.map {
      SentenceAndOffenceWithReleaseArrangements(
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
        offence = it,
        lineSequence = lineSequence,
        caseSequence = caseSequence,
        caseReference = null,
        fineAmount = null,
        courtDescription = null,
        consecutiveToSequence = null,
        isSDSPlus = true,
        hasAnSDSEarlyReleaseExclusion = SDSEarlyReleaseExclusionType.NO,
      )
    }
  }

  val newSentenceAndOffences = newOffences.map {
    SentenceAndOffenceWithReleaseArrangements(
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
      offence = it,
      lineSequence = lineSequence,
      caseSequence = caseSequence,
      caseReference = null,
      fineAmount = null,
      courtDescription = null,
      consecutiveToSequence = null,
      isSDSPlus = true,
      hasAnSDSEarlyReleaseExclusion = SDSEarlyReleaseExclusionType.NO,
    )
  }
  val calculationRequest = CalculationRequest(sentenceAndOffences = objectToJson(listOf(sentenceAndOffences), jacksonObjectMapper().findAndRegisterModules()))
  val changedCalculationRequest = CalculationRequest(sentenceAndOffences = objectToJson(listOf(changedSentenceAndOffences), jacksonObjectMapper().findAndRegisterModules()))
}
