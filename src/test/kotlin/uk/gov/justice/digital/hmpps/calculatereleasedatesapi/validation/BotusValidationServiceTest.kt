package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.FeatureToggles
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.NormalisedSentenceAndOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SDSEarlyReleaseExclusionType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceAndOffenceWithReleaseArrangements
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.OffenderOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonApiSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonerDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceTerms
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi.BookingAndSentenceAdjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.BOTUS_CONSECUTIVE_OR_CONCURRENT_TO_OTHER_SENTENCE
import java.time.LocalDate

class BotusValidationServiceTest {
  private val featureToggles = mock<FeatureToggles>()
  private val botusValidationService = BotusValidationService(featureToggles)

  @Test
  fun `should skip validation when botusConsecutiveJourney feature toggle is enabled`() {
    whenever(featureToggles.botusConsecutiveJourney).thenReturn(true)

    val messages = botusValidationService.validate(SOURCE_DATA)

    assertThat(messages).isEmpty()
  }

  @Test
  fun `should perform validation when botusConsecutiveJourney feature toggle is disabled`() {
    whenever(featureToggles.botusConsecutiveJourney).thenReturn(false)

    val messages = botusValidationService.validate(SOURCE_DATA)

    assertThat(messages).containsExactly(ValidationMessage(BOTUS_CONSECUTIVE_OR_CONCURRENT_TO_OTHER_SENTENCE))
  }

  companion object {
    private val BASE_SENTENCE = NormalisedSentenceAndOffence(
      bookingId = 1L,
      sentenceSequence = 7,
      lineSequence = 153,
      caseSequence = 154,
      sentenceDate = LocalDate.of(2018, 5, 1),
      terms = listOf(
        SentenceTerms(5, 0, 0, 0, SentenceTerms.IMPRISONMENT_TERM_CODE),
      ),
      sentenceCalculationType = SentenceCalculationType.ADIMP.name,
      sentenceCategory = "2003",
      sentenceStatus = "a",
      sentenceTypeDescription = "This is a sentence type",
      offence = OffenderOffence(
        1,
        LocalDate.of(2015, 4, 1),
        null,
        "A123456",
        "TEST OFFENCE 2",
      ),
      caseReference = null,
      fineAmount = null,
      courtDescription = null,
      consecutiveToSequence = null,
    )

    private val VALID_PRISONER =
      PrisonerDetails(offenderNo = "A12345B", bookingId = 1, dateOfBirth = LocalDate.of(1, 2, 3))
    private val VALID_ADJUSTMENTS = BookingAndSentenceAdjustments(emptyList(), emptyList())

    private val CONSECUTIVE_BOTUS_SENTENCES = listOf(
      BASE_SENTENCE.copy(
        sentenceSequence = 1,
      ),
      BASE_SENTENCE.copy(
        sentenceSequence = 2,
        consecutiveToSequence = 1,
        sentenceCalculationType = "BOTUS",
        terms = listOf(SentenceTerms(days = 7, code = "IMP")),
      ),
    )

    val SOURCE_DATA = PrisonApiSourceData(
      sentenceAndOffences = CONSECUTIVE_BOTUS_SENTENCES.map {
        SentenceAndOffenceWithReleaseArrangements(
          source = it,
          isSdsPlus = false,
          isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
          isSDSPlusOffenceInPeriod = false,
          hasAnSDSExclusion = SDSEarlyReleaseExclusionType.NO,
        )
      },
      prisonerDetails = VALID_PRISONER,
      bookingAndSentenceAdjustments = VALID_ADJUSTMENTS,
      offenderFinePayments = listOf(),
      returnToCustodyDate = null,
    )
  }
}
