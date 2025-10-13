package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mockito.mock
import org.mockito.junit.jupiter.MockitoExtension
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.NormalisedSentenceAndOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SDSEarlyReleaseExclusionType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceAndOffenceWithReleaseArrangements
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.CalculationSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.OffenderOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonApiSentenceAndOffences
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceTerms
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.validator.ConsecutiveSentenceValidator
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.validator.ConsecutiveToUniqueSentenceValidator
import java.time.LocalDate

@ExtendWith(MockitoExtension::class)
class SentenceValidationServiceTest {

  @Test
  fun `Concurrent Consecutive validation returns longest duration where multiple chains are affected`() {
    val sentences = listOf(
      activeOffence,
      activeOffence.copy(
        caseSequence = 2,
        sentenceSequence = 2,
        consecutiveToSequence = 1,
        terms = listOf(
          SentenceTerms(1, 0, 0, 0, code = "IMP"),
        ),
      ),
      activeOffence.copy(
        caseSequence = 3,
        sentenceSequence = 3,
        consecutiveToSequence = 1,
        terms = listOf(
          SentenceTerms(1, 0, 0, 0, code = "IMP"),
        ),
      ),
      activeOffence.copy(
        caseSequence = 4,
        sentenceSequence = 4,
        consecutiveToSequence = 3,
        terms = listOf(
          SentenceTerms(0, 0, 0, 2, code = "IMP"),
        ),
      ),
      activeOffence.copy(
        caseSequence = 5,
        sentenceSequence = 5,
        consecutiveToSequence = 4,
        terms = listOf(
          SentenceTerms(0, 0, 0, 1, code = "IMP"),
        ),
      ),
      activeOffence.copy(
        caseSequence = 6,
        sentenceSequence = 6,
        consecutiveToSequence = 5,
        terms = listOf(
          SentenceTerms(0, 0, 0, 1, code = "IMP"),
        ),
      ),
      activeOffence.copy(
        caseSequence = 7,
        sentenceSequence = 7,
        consecutiveToSequence = 4,
        terms = listOf(
          SentenceTerms(0, 0, 0, 2, code = "IMP"),
        ),
      ),
      activeOffence.copy(
        caseSequence = 8,
        sentenceSequence = 7,
        consecutiveToSequence = 7,
        terms = listOf(
          SentenceTerms(0, 0, 0, 2, code = "IMP"),
        ),
      ),
    )
    val result = ConsecutiveToUniqueSentenceValidator().validate(CalculationSourceData(sentences, mock(), mock(), mock(), mock()))
    assertTrue(result.count() == 1)
    assertEquals("1 years 1 months 0 weeks 4 days", result.first().message)
  }

  @Test
  fun `Concurrent Consecutive validation ignore sentences derided from multiple offences against one NOMIS sentence`() {
    val sentences = listOf(
      activeOffence,
      activeOffence.copy(
        caseSequence = 2,
        sentenceSequence = 2,
        consecutiveToSequence = 1,
        terms = listOf(
          SentenceTerms(1, 0, 0, 0, code = "IMP"),
        ),
      ),
      activeOffence.copy(
        caseSequence = 2,
        sentenceSequence = 2,
        consecutiveToSequence = 1,
        terms = listOf(
          SentenceTerms(1, 0, 0, 0, code = "IMP"),
        ),
      ),
    )
    val result = ConsecutiveToUniqueSentenceValidator().validate(CalculationSourceData(sentences, mock(), mock(), mock(), mock()))
    assertTrue(result.count() == 0)
  }

  @Test
  fun `Sentence cannot be made consecutive to a sentence imposed after`() {
    val laterSentence = activeOffence.copy(
      sentenceDate = LocalDate.of(2025, 1, 1),
      sentenceSequence = 1,
      lineSequence = 1,
      consecutiveToSequence = null,
    )
    val earlierSentence = activeOffence.copy(
      sentenceDate = LocalDate.of(2024, 1, 1),
      sentenceSequence = 2,
      lineSequence = 2,
      consecutiveToSequence = 1,
    )

    val sentences = listOf(laterSentence, earlierSentence)
    val result = ConsecutiveSentenceValidator().validate(CalculationSourceData(sentences, mock(), mock(), mock(), mock()))

    assertThat(result).hasSize(1)
    assertThat(result[0].message).isEqualTo("Court case 1 NOMIS line number 2 cannot be consecutive to a sentence that has a later date")
  }

  companion object {
    val offence = OffenderOffence(
      1L,
      LocalDate.of(2015, 1, 1),
      null,
      "ADIMP",
      "description",
      listOf("A"),
    )
    val prisonApiSentenceAndOffences = PrisonApiSentenceAndOffences(
      1,
      1,
      1,
      1,
      null,
      "A",
      "A",
      sentenceCalculationType = SentenceCalculationType.ADIMP.name,
      "",
      LocalDate.now(),
      terms = listOf(
        SentenceTerms(0, 1, 0, 0, code = "IMP"),
      ),
      offences = listOf(offence),
    )
    val activeOffence = SentenceAndOffenceWithReleaseArrangements(
      NormalisedSentenceAndOffence(prisonApiSentenceAndOffences, offence),
      false,
      false,
      false,
      SDSEarlyReleaseExclusionType.NO,
    )
  }
}
