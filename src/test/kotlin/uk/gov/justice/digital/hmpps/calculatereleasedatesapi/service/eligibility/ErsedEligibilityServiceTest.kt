package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.eligibility

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SDSEarlyReleaseExclusionType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceAndOffenceWithReleaseArrangements
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.OffenderOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceTerms
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.manageoffencesapi.ToreraSchedulePartCodes
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.ImportantDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.ManageOffencesService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.PrisonService
import java.time.LocalDate

@ExtendWith(MockitoExtension::class)
class ErsedEligibilityServiceTest {

  @Mock
  lateinit var manageOffencesService: ManageOffencesService

  @Mock
  lateinit var prisonService: PrisonService

  lateinit var service: ErsedEligibilityService

  @BeforeEach
  fun setUp() {
    service = ErsedEligibilityService(manageOffencesService, prisonService)
  }

  @ParameterizedTest
  @MethodSource("ineligibleSentences")
  fun `should return ineligible when sentence has invalid type`(sentence: SentenceAndOffenceWithReleaseArrangements) {
    val bookingId = 123L
    whenever(prisonService.getSentencesAndOffences(any(), any())).thenReturn(listOf(sentence))

    val result = service.sentenceIsEligible(bookingId)

    assertThat(result).isEqualTo(ErsedEligibilityService.ErsedEligibility(false, "No valid ersed sentence types"))
  }

  @Test
  fun `should return eligible when at least one sentence has valid type`() {
    val bookingId = 123L
    val edsSentence = createSentence(SentenceCalculationType.EDS18.name, "OFF_PART2")
    val noneEdsSentence = createSentence(SentenceCalculationType.AFINE.name, "NOT_RELEVANT")
    val partsMap = mapOf(1 to listOf("OFF_PART1"))
    whenever(prisonService.getSentencesAndOffences(any(), any())).thenReturn(listOf(edsSentence, noneEdsSentence))
    whenever(manageOffencesService.getToreraCodesByParts()).thenReturn(ToreraSchedulePartCodes(partsMap))

    val result = service.sentenceIsEligible(bookingId)

    assertThat(result).isEqualTo(ErsedEligibilityService.ErsedEligibility(true))
  }

  @Test
  fun `should return ineligible when EDS sentence has 19ZA part 1 offence`() {
    val bookingId = 456L
    val edsSentence = createSentence(SentenceCalculationType.EDS18.name, "OFF_PART1")
    val partsMap = mapOf(1 to listOf("OFF_PART1"))

    whenever(prisonService.getSentencesAndOffences(bookingId)).thenReturn(listOf(edsSentence))
    whenever(manageOffencesService.getToreraCodesByParts()).thenReturn(ToreraSchedulePartCodes(partsMap))

    val result = service.sentenceIsEligible(bookingId)

    assertThat(result).isEqualTo(ErsedEligibilityService.ErsedEligibility(false, "EDS sentence with 19ZA part 1 offence"))
  }

  @Test
  fun `should return eligible when EDS sentence has no 19ZA part 1 offence`() {
    val bookingId = 789L
    val edsSentence = createSentence(SentenceCalculationType.EDS21.name, "OFF_NOT_PART1")
    val noneEdsSentence = createSentence(SentenceCalculationType.AFINE.name, "NOT_RELEVANT")
    val partsMap = mapOf(1 to listOf("OFF_PART1"))

    whenever(prisonService.getSentencesAndOffences(bookingId)).thenReturn(listOf(edsSentence, noneEdsSentence))
    whenever(manageOffencesService.getToreraCodesByParts()).thenReturn(ToreraSchedulePartCodes(partsMap))

    val result = service.sentenceIsEligible(bookingId)

    assertThat(result).isEqualTo(ErsedEligibilityService.ErsedEligibility(true))
  }

  companion object {
    @JvmStatic
    fun ineligibleSentences() = listOf(
      createSentence(SentenceCalculationType.AFINE.name, "OFF_PART1"),
      createSentence(SentenceCalculationType.DTO.name, "OFF_PART2"),
      createSentence(SentenceCalculationType.DTO_ORA.name, "OFF_PART3"),
      createSentence(SentenceCalculationType.DTO_ORA.name, "OFF_PART4"),
      createSentence(SentenceCalculationType.LR.name, "OFF_PART5"), // recall sentence type
    )

    private fun createSentence(type: String, code: String): SentenceAndOffenceWithReleaseArrangements = SentenceAndOffenceWithReleaseArrangements(
      bookingId = 1L,
      sentenceSequence = 3,
      lineSequence = 2,
      caseSequence = 1,
      sentenceDate = ImportantDates.SDS_PLUS_COMMENCEMENT_DATE,
      terms = listOf(
        SentenceTerms(years = 8),
      ),
      sentenceStatus = "IMP",
      sentenceCategory = "CAT",
      sentenceCalculationType = type,
      sentenceTypeDescription = "ADMIP",
      offence = OffenderOffence(1L, LocalDate.of(2015, 1, 1), null, code, "description", listOf("A")),
      caseReference = null,
      fineAmount = null,
      courtDescription = null,
      consecutiveToSequence = null,
      isSDSPlus = false,
      isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
      isSDSPlusOffenceInPeriod = false,
      hasAnSDSEarlyReleaseExclusion = SDSEarlyReleaseExclusionType.NO,
    )
  }
}
