package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mockito.mock
import org.mockito.junit.jupiter.MockitoExtension
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.adjustmentsapi.model.AdjustmentDto
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AdjustmentsSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.NormalisedSentenceAndOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SDSEarlyReleaseExclusionType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceAndOffenceWithReleaseArrangements
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.CalculationSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.OffenderOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonApiSentenceAndOffences
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceTerms
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.validator.RemandPeriodsAgainstSentenceDateValidator
import java.time.LocalDate

@ExtendWith(MockitoExtension::class)
class RemandPeriodsAgainstSentenceDateValidatorTest {
  val validator = RemandPeriodsAgainstSentenceDateValidator(ValidationUtilities())

  @Test
  fun `Validation fails if remand dates are after sentence date`() {
    val sentenceDate = LocalDate.of(2020, 1, 1)

    val testSentence = sentence.copy(
      bookingId = 1,
      sentenceSequence = 1,
      sentenceDate = sentenceDate,
    )
    val remandAfterTestSentence = remand.copy(
      fromDate = LocalDate.of(2021, 12, 1),
      toDate = LocalDate.of(2021, 12, 31),
      bookingId = 1,
      sentenceSequence = 1,
    )

    val sourceData = CalculationSourceData(
      listOf(testSentence),
      mock(),
      AdjustmentsSourceData(adjustmentsApiData = listOf(remandAfterTestSentence)),
      mock(),
      mock(),
    )

    val validationResult = validator.validate(sourceData)

    assertThat(validationResult).isNotEmpty()
    assertThat(validationResult[0]).isEqualTo(ValidationMessage(ValidationCode.REMAND_ON_OR_AFTER_SENTENCE_DATE, listOf(testSentence.caseSequence.toString(), testSentence.lineSequence.toString())))
  }

  @Test
  fun `Validation passes if data across multiple bookings with same sentence sequences`() {
    val earliestSentenceDate = LocalDate.of(2020, 1, 1)
    val laterSentenceDate = LocalDate.of(2022, 1, 1)

    val earlierSentence = sentence.copy(
      bookingId = 1,
      sentenceSequence = 1,
      sentenceDate = earliestSentenceDate,
    )
    val laterSentence = sentence.copy(
      bookingId = 2,
      sentenceSequence = 1,
      sentenceDate = laterSentenceDate,
    )
    val remandForLaterSentence = remand.copy(
      fromDate = LocalDate.of(2021, 12, 1),
      toDate = LocalDate.of(2021, 12, 31),
      bookingId = 2,
      sentenceSequence = 1,
    )

    val sourceData = CalculationSourceData(
      listOf(laterSentence, earlierSentence),
      mock(),
      AdjustmentsSourceData(adjustmentsApiData = listOf(remandForLaterSentence)),
      mock(),
      mock(),
    )

    val validationResult = validator.validate(sourceData)

    assertThat(validationResult).isEmpty()
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
    val sentence = SentenceAndOffenceWithReleaseArrangements(
      NormalisedSentenceAndOffence(
        PrisonApiSentenceAndOffences(
          1,
          1,
          1,
          1,
          null,
          "A",
          "A",
          sentenceCalculationType = SentenceCalculationType.ADIMP.name,
          "",
          LocalDate.of(2015, 1, 2),
          terms = listOf(
            SentenceTerms(0, 1, 0, 0, code = "IMP"),
          ),
          offences = listOf(offence),
        ),
        offence,
      ),
      false,
      false,
      false,
      SDSEarlyReleaseExclusionType.NO,
    )

    val remand = AdjustmentDto(
      bookingId = 1,
      person = "A1234BC",
      adjustmentType = AdjustmentDto.AdjustmentType.REMAND,
      days = 0,
    )
  }
}
