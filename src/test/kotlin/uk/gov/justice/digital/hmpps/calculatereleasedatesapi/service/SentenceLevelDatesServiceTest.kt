package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.TestUtil
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationRule
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.CRD
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.ESED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.HDCED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.SLED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceIdentificationTrack
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationOutput
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationTrigger
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Duration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offender
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ReleaseDateCalculationBreakdown
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ReleaseDateTypes
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SDSEarlyReleaseExclusionType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceAdjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceLevelDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.StandardDeterminateSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.UnadjustedReleaseDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationRequestSentenceOutcomeRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationRequestSentenceRepository
import java.time.LocalDate
import java.time.Period
import java.time.temporal.ChronoUnit.DAYS
import java.util.UUID

class SentenceLevelDatesServiceTest {

  private val calculationRequestSentenceRepository: CalculationRequestSentenceRepository = mock()
  private val calculationRequestSentenceOutcomeRepository: CalculationRequestSentenceOutcomeRepository = mock()
  private val objectMapper: ObjectMapper = TestUtil.objectMapper()
  private val service = SentenceLevelDatesService(calculationRequestSentenceRepository, calculationRequestSentenceOutcomeRepository, objectMapper)

  @Test
  fun `filter out uninitialised sentence identification track which may exist as part of a non-persisted calculation such as validation`() {
    val testIdentifierUUID = UUID.randomUUID()
    val sentence = STANDARD_SENTENCE.copy(identifier = testIdentifierUUID)
    assertThat(sentence.isIdentificationTrackInitialized()).isFalse
    assertThat(sentence.isCalculationInitialised()).isFalse

    val extracted = service.extractSentenceLevelDates(
      CalculationOutput(
        listOf(sentence),
        emptyList(),
        CalculationResult(
          effectiveSentenceLength = Period.of(1, 0, 0),
          dates = mapOf(
            SLED to LocalDate.of(2028, 6, 15),
            CRD to LocalDate.of(2023, 8, 4),
            HDCED to LocalDate.of(2023, 2, 6),
            ESED to LocalDate.of(2026, 2, 2),
          ),
          usedPreviouslyRecordedSLED = null,
          breakdownByReleaseDateType = mapOf(
            SLED to ReleaseDateCalculationBreakdown(
              releaseDate = LocalDate.of(2028, 6, 15),
              unadjustedDate = LocalDate.of(2026, 2, 2),
              rules = setOf(CalculationRule.PREVIOUSLY_RECORDED_SLED_USED),
            ),
          ),
        ),
      ),
    )
    assertThat(extracted).isEmpty()
  }

  @Test
  fun `filter out uninitialised sentences calculation which may exist as part of a non-persisted calculation such as validation`() {
    val testIdentifierUUID = UUID.randomUUID()
    val sentence = STANDARD_SENTENCE.copy(identifier = testIdentifierUUID)
    sentence.identificationTrack = SentenceIdentificationTrack.SDS
    assertThat(sentence.isIdentificationTrackInitialized()).isTrue
    assertThat(sentence.isCalculationInitialised()).isFalse

    val extracted = service.extractSentenceLevelDates(
      CalculationOutput(
        listOf(sentence),
        emptyList(),
        CalculationResult(
          effectiveSentenceLength = Period.of(1, 0, 0),
          dates = mapOf(
            SLED to LocalDate.of(2028, 6, 15),
            CRD to LocalDate.of(2023, 8, 4),
            HDCED to LocalDate.of(2023, 2, 6),
            ESED to LocalDate.of(2026, 2, 2),
          ),
          usedPreviouslyRecordedSLED = null,
          breakdownByReleaseDateType = mapOf(
            SLED to ReleaseDateCalculationBreakdown(
              releaseDate = LocalDate.of(2028, 6, 15),
              unadjustedDate = LocalDate.of(2026, 2, 2),
              rules = setOf(CalculationRule.PREVIOUSLY_RECORDED_SLED_USED),
            ),
          ),
        ),
      ),
    )
    assertThat(extracted).isEmpty()
  }

  @Test
  fun `return initialised sentences`() {
    val testIdentifierUUID = UUID.randomUUID()
    val sentence = STANDARD_SENTENCE.copy(identifier = testIdentifierUUID)
    sentence.identificationTrack = SentenceIdentificationTrack.SDS
    sentence.releaseDateTypes = ReleaseDateTypes(listOf(SLED), sentence, OFFENDER)
    sentence.sentenceCalculation = SentenceCalculation(
      UnadjustedReleaseDate(
        sentence,
        mock(),
        CalculationTrigger(LocalDate.now()),
      ),
      SentenceAdjustments(),
      false,
    )
    assertThat(sentence.isIdentificationTrackInitialized()).isTrue
    assertThat(sentence.isCalculationInitialised()).isTrue

    val sledBreakdown = ReleaseDateCalculationBreakdown(
      releaseDate = LocalDate.of(2028, 6, 15),
      unadjustedDate = LocalDate.of(2026, 2, 2),
      rules = setOf(CalculationRule.PREVIOUSLY_RECORDED_SLED_USED),
    )

    sentence.sentenceCalculation.breakdownByReleaseDateType[SLED] = sledBreakdown

    val extracted = service.extractSentenceLevelDates(
      CalculationOutput(
        listOf(sentence),
        emptyList(),
        CalculationResult(
          effectiveSentenceLength = Period.of(1, 0, 0),
          dates = mapOf(SLED to LocalDate.of(2028, 6, 15)),
          usedPreviouslyRecordedSLED = null,
          breakdownByReleaseDateType = mapOf(SLED to sledBreakdown),
        ),
      ),
    )
    assertThat(extracted).containsExactly(SentenceLevelDates(sentence = sentence, groupIndex = 0, impactsFinalReleaseDate = false, releaseMultiplier = ReleaseMultiplier.ONE_HALF, dates = mapOf(SLED to LocalDate.of(2020, 1, 1))))
  }

  companion object {
    private val OFFENDER = Offender(
      reference = "A1234BC",
      dateOfBirth = LocalDate.of(1992, 1, 1),
    )
    private val ONE_DAY_DURATION = Duration(mapOf(DAYS to 1L))
    private val OFFENCE = Offence(LocalDate.of(2020, 1, 1))
    private val STANDARD_SENTENCE = StandardDeterminateSentence(
      OFFENCE,
      ONE_DAY_DURATION,
      LocalDate.of(2020, 1, 1),
      isSDSPlus = false,
      hasAnSDSEarlyReleaseExclusion = SDSEarlyReleaseExclusionType.NO,
    )
  }
}
