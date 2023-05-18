package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAndOffences
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType
import java.time.LocalDate

class ManualCalculationServiceTest {
  private val prisonService = mock<PrisonService>()
  private val manualCalculationService = ManualCalculationService(prisonService)

  @Nested
  inner class IndeterminateSentencesTests {
    @Test
    fun `Check the presence of indeterminate sentences returns true`() {
      whenever(prisonService.getSentencesAndOffences(BOOKING_ID)).thenReturn(
        listOf(
          BASE_DETERMINATE_SENTENCE.copy(sentenceCalculationType = SentenceCalculationType.TWENTY.name),
          BASE_DETERMINATE_SENTENCE,
        ),
      )

      val hasIndeterminateSentences = manualCalculationService.hasIndeterminateSentences(BOOKING_ID)

      assertThat(hasIndeterminateSentences).isTrue()
    }

    @Test
    fun `Check the absence of indeterminate sentences returns false`() {
      whenever(prisonService.getSentencesAndOffences(BOOKING_ID)).thenReturn(
        listOf(
          BASE_DETERMINATE_SENTENCE.copy(sentenceCalculationType = SentenceCalculationType.FTR.name),
          BASE_DETERMINATE_SENTENCE,
        ),
      )

      val hasIndeterminateSentences = manualCalculationService.hasIndeterminateSentences(BOOKING_ID)

      assertThat(hasIndeterminateSentences).isFalse()
    }
  }

  private companion object {
    private const val BOOKING_ID = 12345L

    private val BASE_DETERMINATE_SENTENCE = SentenceAndOffences(
      bookingId = BOOKING_ID,
      sentenceSequence = 1,
      lineSequence = 1,
      caseSequence = 1,
      sentenceDate = LocalDate.of(2022, 1, 1),
      sentenceStatus = "IMP",
      sentenceCategory = "CAT",
      sentenceCalculationType = SentenceCalculationType.ADIMP.name,
      sentenceTypeDescription = "ADMIP",
    )
  }
}
