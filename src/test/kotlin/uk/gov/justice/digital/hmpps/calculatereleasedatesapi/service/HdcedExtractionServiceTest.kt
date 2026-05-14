package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.CALLS_REAL_METHODS
import org.mockito.Mockito.mockStatic
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.FeatureToggles
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationRule
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculableSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Duration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offender
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ReleaseDateCalculationBreakdown
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ReleaseDateTypes
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SDSReleaseArrangements
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SopcSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.StandardDeterminateSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.ImportantDates.PROGRESSION_COMMENCEMENT_DATE
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.sentence.SentencesExtractionService
import java.time.LocalDate
import java.time.temporal.ChronoUnit.MONTHS

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HdcedExtractionServiceTest {

  @Mock
  lateinit var extractionService: SentencesExtractionService

  @Mock
  lateinit var featureToggles: FeatureToggles

  private lateinit var service: HdcedExtractionService

  private val preRepealDate: LocalDate = PROGRESSION_COMMENCEMENT_DATE.minusDays(1)

  @BeforeEach
  fun setup() {
    service = HdcedExtractionService(extractionService, featureToggles)
  }

  private val today = preRepealDate

  private fun <T> withNow(now: LocalDate, block: () -> T): T = mockStatic(LocalDate::class.java, CALLS_REAL_METHODS).use { mockedLocalDate ->
    mockedLocalDate.`when`<LocalDate> { LocalDate.now() }.thenReturn(now)
    block()
  }

  @Test
  fun `should return null if any sentence is SDS+`() {
    val sentences = listOf(
      createTestSentence(isSdsPlus = true),
    )
    val result = withNow(preRepealDate) { service.extractManyHomeDetentionCurfewEligibilityDate(sentences, sentences) }
    assertThat(result).isNull()
  }

  @Test
  fun `should return null if latest eligible sentence has no HDCED`() {
    val sentences = listOf(createTestSentence(hdced = null))
    whenever(
      extractionService.mostRecentSentenceOrNull(
        sentences.filter { !it.isRecall() && !it.isDto() },
        SentenceCalculation::releaseDate,
      ),
    ).thenReturn(sentences[0])

    val result = withNow(preRepealDate) { service.extractManyHomeDetentionCurfewEligibilityDate(sentences, sentences) }
    assertThat(result).isNull()
  }

  @Test
  fun `should return HDCED if latest eligible sentence has HDCED`() {
    val sentences = listOf(createTestSentence(hdced = today.plusDays(100)))
    whenever(
      extractionService.mostRecentSentenceOrNull(
        sentences.filter { !it.isRecall() && !it.isDto() },
        SentenceCalculation::releaseDate,
      ),
    ).thenReturn(sentences[0])

    val result = withNow(preRepealDate) { service.extractManyHomeDetentionCurfewEligibilityDate(sentences, sentences) }
    assertThat(result).isNull()
  }

  @Test
  fun `should return null if no valid HDCED sentence after 14-day rule`() {
    val sentences = listOf(createTestSentence())
    whenever(
      extractionService.mostRecentSentenceOrNull(
        sentences.filter { !it.isRecall() && !it.isDto() },
        SentenceCalculation::releaseDate,
      ),
    ).thenReturn(sentences[0])
    whenever(
      extractionService.mostRecentSentenceOrNull(
        sentences.filter { !today.isBefore(it.sentencedAt.plusDays(14)) },
        SentenceCalculation::homeDetentionCurfewEligibilityDate,
      ),
    ).thenReturn(null)

    val result = withNow(preRepealDate) { service.extractManyHomeDetentionCurfewEligibilityDate(sentences, sentences) }
    assertThat(result).isNull()
  }

  @Test
  fun `should return null if final release date is not after HDCED`() {
    val hdced = today.plusDays(10)
    val releaseDate = today.plusDays(5)
    val sentences = listOf(createTestSentence(hdced = hdced, releaseDate = releaseDate))
    val mostRecentSentences = listOf(createTestSentence(releaseDate = releaseDate))

    whenever(
      extractionService.mostRecentSentenceOrNull(
        sentences.filter { !it.isRecall() && !it.isDto() },
        SentenceCalculation::releaseDate,
      ),
    ).thenReturn(sentences[0])
    whenever(
      extractionService.mostRecentSentenceOrNull(
        sentences.filter { !releaseDate.isBefore(it.sentencedAt.plusDays(14)) },
        SentenceCalculation::homeDetentionCurfewEligibilityDate,
      ),
    ).thenReturn(sentences[0])

    val result = withNow(preRepealDate) { service.extractManyHomeDetentionCurfewEligibilityDate(sentences, mostRecentSentences) }
    assertThat(result).isNull()
  }

  @Test
  fun `should return original HDCED if no conflicting sentences and no special rules`() {
    val hdced = today.plusDays(10)
    val releaseDate = today.plusDays(20)
    val hdcedSentence = createTestSentence(hdced = hdced, releaseDate = releaseDate)
    val sentences = listOf(hdcedSentence)
    val mostRecentSentences = listOf(createTestSentence(releaseDate = releaseDate))

    whenever(featureToggles.applyPostHdcedRepealRules).thenReturn(false)
    whenever(
      extractionService.mostRecentSentenceOrNull(
        sentences.filter { !it.isRecall() && !it.isDto() },
        SentenceCalculation::releaseDate,
      ),
    ).thenReturn(hdcedSentence)
    whenever(
      extractionService.mostRecentSentenceOrNull(
        sentences.filter { !releaseDate.isBefore(it.sentencedAt.plusDays(14)) },
        SentenceCalculation::homeDetentionCurfewEligibilityDate,
      ),
    ).thenReturn(hdcedSentence)
    whenever(
      extractionService.mostRecentSentenceOrNull(
        emptyList(),
        SentenceCalculation::releaseDate,
      ),
    ).thenReturn(null)

    val result = withNow(preRepealDate) { service.extractManyHomeDetentionCurfewEligibilityDate(sentences, mostRecentSentences) }
    assertThat(result).isNotNull
    assertThat(result!!.first).isEqualTo(hdced)
    assertThat(result.second).isEqualTo(hdcedSentence.sentenceCalculation.breakdownByReleaseDateType[ReleaseDateType.HDCED])
  }

  @Test
  fun `should return adjusted HDCED if conflicting sentence exists`() {
    val originalHdced = today.plusDays(10)
    val conflictingReleaseDate = today.plusDays(30)
    val hdcedSentence = createTestSentence(hdced = originalHdced, releaseDate = today.plusDays(20))
    val conflictingSentence = createTestSentence(releaseDate = conflictingReleaseDate, hdced = null)
    val offender = Offender("A1234AA", LocalDate.of(1980, 1, 1))
    conflictingSentence.releaseDateTypes = ReleaseDateTypes(listOf(ReleaseDateType.CRD), conflictingSentence, offender)

    doReturn(conflictingReleaseDate).whenever(conflictingSentence.sentenceCalculation).releaseDateDefaultedByCommencement()
    doReturn(false).whenever(conflictingSentence.sentenceCalculation).isImmediateRelease()
    val sentences = listOf(hdcedSentence, conflictingSentence)
    val mostRecentSentences = listOf(createTestSentence(releaseDate = conflictingReleaseDate))
    doReturn(conflictingReleaseDate).whenever(mostRecentSentences[0].sentenceCalculation).releaseDate

    whenever(featureToggles.applyPostHdcedRepealRules).thenReturn(false)

    // Use thenAnswer to differentiate which sentence to return based on which property is queried
    whenever(extractionService.mostRecentSentenceOrNull(any(), any(), any())).thenAnswer { invocation ->
      val property = invocation.getArgument<kotlin.reflect.KProperty1<SentenceCalculation, LocalDate?>>(1)
      when (property) {
        SentenceCalculation::homeDetentionCurfewEligibilityDate -> hdcedSentence
        SentenceCalculation::releaseDate -> {
          val list = invocation.getArgument<List<CalculableSentence>>(0)
          if (list.any { it === conflictingSentence } && list.none { it === hdcedSentence }) conflictingSentence else hdcedSentence
        }
        else -> null
      }
    }

    val result = withNow(preRepealDate) { service.extractManyHomeDetentionCurfewEligibilityDate(sentences, mostRecentSentences) }
    assertThat(result).isNotNull
    assertThat(result!!.first).isEqualTo(conflictingReleaseDate)
    assertThat(result.second.rules).contains(CalculationRule.HDCED_ADJUSTED_TO_CONCURRENT_CONDITIONAL_RELEASE)
    assertThat(result.second.unadjustedDate).isEqualTo(originalHdced)
  }

  @Test
  fun `Post-Repeal - should return null when calc date is on or after commencement`() {
    val mockToday = PROGRESSION_COMMENCEMENT_DATE.plusDays(1)
    val hdced = PROGRESSION_COMMENCEMENT_DATE.plusDays(10)
    val releaseDate = hdced.plusDays(10)
    val hdcedSentence = createTestSentence(hdced = hdced, releaseDate = releaseDate, isTerm = true)
    val sentences = listOf(hdcedSentence)
    val mostRecentSentences = listOf(createTestSentence(releaseDate = releaseDate))

    whenever(featureToggles.applyPostHdcedRepealRules).thenReturn(true)
    whenever(
      extractionService.mostRecentSentenceOrNull(
        sentences.filter { !it.isRecall() && !it.isDto() },
        SentenceCalculation::releaseDate,
      ),
    ).thenReturn(hdcedSentence)
    whenever(
      extractionService.mostRecentSentenceOrNull(
        sentences.filter { !releaseDate.isBefore(it.sentencedAt.plusDays(14)) },
        SentenceCalculation::homeDetentionCurfewEligibilityDate,
      ),
    ).thenReturn(hdcedSentence)
    whenever(
      extractionService.mostRecentSentenceOrNull(
        emptyList(),
        SentenceCalculation::releaseDate,
      ),
    ).thenReturn(null)

    val result = withNow(mockToday) {
      service.extractManyHomeDetentionCurfewEligibilityDate(sentences, mostRecentSentences)
    }
    assertThat(result).isNull()
  }

  @Test
  fun `Post-Repeal - should return HDCED when calc date is before commencement`() {
    val mockToday = PROGRESSION_COMMENCEMENT_DATE.minusDays(1)
    val hdced = PROGRESSION_COMMENCEMENT_DATE.minusDays(10)
    val releaseDate = hdced.plusDays(10)
    val hdcedSentence = createTestSentence(hdced = hdced, releaseDate = releaseDate, isTerm = true)
    val sentences = listOf(hdcedSentence)
    val mostRecentSentences = listOf(createTestSentence(releaseDate = releaseDate))

    whenever(featureToggles.applyPostHdcedRepealRules).thenReturn(true)
    whenever(
      extractionService.mostRecentSentenceOrNull(
        sentences.filter { !it.isRecall() && !it.isDto() },
        SentenceCalculation::releaseDate,
      ),
    ).thenReturn(hdcedSentence)
    whenever(
      extractionService.mostRecentSentenceOrNull(
        sentences.filter { !releaseDate.isBefore(it.sentencedAt.plusDays(14)) },
        SentenceCalculation::homeDetentionCurfewEligibilityDate,
      ),
    ).thenReturn(hdcedSentence)
    whenever(
      extractionService.mostRecentSentenceOrNull(
        emptyList(),
        SentenceCalculation::releaseDate,
      ),
    ).thenReturn(null)

    val result = withNow(mockToday) {
      service.extractManyHomeDetentionCurfewEligibilityDate(sentences, mostRecentSentences)
    }
    assertThat(result).isNotNull
    assertThat(result!!.first).isEqualTo(hdced)
  }

  private fun createTestSentence(
    releaseDate: LocalDate = today,
    hdced: LocalDate? = today,
    isSdsPlus: Boolean = false,
    isSection250: Boolean = false,
    isTerm: Boolean = false,
    sentencedAt: LocalDate = today.minusYears(1),
  ): CalculableSentence {
    val offence = Offence(committedAt = sentencedAt.minusDays(1))
    val duration = Duration(mutableMapOf(MONTHS to 1L))

    val sentence = if (isTerm) {
      SopcSentence(
        caseReference = "CRN123",
        sentencedAt = sentencedAt,
        offence = offence,
        custodialDuration = duration,
        extensionDuration = duration,
      )
    } else {
      val releaseArrangements = mock<SDSReleaseArrangements> {
        on { isSDSPlusEligibleSentenceTypeLengthAndOffence } doReturn isSdsPlus
        on { this.isSection250 } doReturn isSection250
      }
      StandardDeterminateSentence(
        sentencedAt = sentencedAt,
        offence = offence,
        duration = duration,
        releaseArrangements = releaseArrangements,
      )
    }

    sentence.sentenceCalculation = mock()

    if (hdced != null) {
      val breakdown = ReleaseDateCalculationBreakdown(releaseDate = hdced)
      whenever(sentence.sentenceCalculation.breakdownByReleaseDateType).thenReturn(mutableMapOf(ReleaseDateType.HDCED to breakdown))
    }

    whenever(sentence.sentenceCalculation.releaseDate).thenReturn(releaseDate)
    whenever(sentence.sentenceCalculation.homeDetentionCurfewEligibilityDate).thenReturn(hdced)

    return sentence
  }
}
