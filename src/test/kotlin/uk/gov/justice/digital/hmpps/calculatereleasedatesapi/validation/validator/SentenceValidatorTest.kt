package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.validator

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.OffenderOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAndOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceTerms
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationUtilities
import java.time.LocalDate

class SentenceValidatorTest {
  private lateinit var validationUtilities: ValidationUtilities
  private lateinit var sentenceValidator: SentenceValidator

  @BeforeEach
  fun setUp() {
    validationUtilities = ValidationUtilities()
    sentenceValidator = SentenceValidator(validationUtilities)
  }

  private fun createSentenceAndOffence(offenceStartDate: LocalDate?): SentenceAndOffence {
    val offenderOffence = OffenderOffence(
      offenderChargeId = 1L,
      offenceStartDate = offenceStartDate,
      offenceEndDate = null,
      offenceCode = "CODE",
      offenceDescription = "desc",
    )
    return object : SentenceAndOffence {
      override val bookingId = 1L
      override val sentenceSequence = 1
      override val lineSequence = 1
      override val caseSequence = 1
      override val consecutiveToSequence: Int? = null
      override val sentenceStatus = "status"
      override val sentenceCategory = "cat"
      override val sentenceCalculationType = "type"
      override val sentenceTypeDescription = "desc"
      override val sentenceDate = LocalDate.now()
      override val terms = emptyList<SentenceTerms>()
      override val offence = offenderOffence
      override val caseReference: String? = null
      override val courtId: String? = null
      override val courtDescription: String? = null
      override val courtTypeCode: String? = null
      override val fineAmount: java.math.BigDecimal? = null
      override val revocationDates = emptyList<LocalDate>()
    }
  }

  @Nested
  inner class ValidateOffenceDateOverOneHundredYearsAgoTests {
    @Test
    fun `returns null if offenceStartDate is null`() {
      val sentenceAndOffence = createSentenceAndOffence(null)
      val result = sentenceValidator.validateOffenceStartDateRange(sentenceAndOffence)
      assertThat(result).isNull()
    }

    @Test
    fun `returns null if offenceStartDate is exactly 100 years ago`() {
      val date = LocalDate.now().minusYears(100)
      val sentenceAndOffence = createSentenceAndOffence(date)
      val result = sentenceValidator.validateOffenceStartDateRange(sentenceAndOffence)
      assertThat(result).isNull()
    }

    @Test
    fun `returns null if offenceStartDate is less than 100 years ago`() {
      val date = LocalDate.now().minusYears(99)
      val sentenceAndOffence = createSentenceAndOffence(date)
      val result = sentenceValidator.validateOffenceStartDateRange(sentenceAndOffence)
      assertThat(result).isNull()
    }

    @Test
    fun `returns ValidationMessage if offenceStartDate is more than 100 years ago`() {
      val date = LocalDate.now().minusYears(101)
      val sentenceAndOffence = createSentenceAndOffence(date)
      val result = sentenceValidator.validateOffenceStartDateRange(sentenceAndOffence)
      assertThat(result).isNotNull()
      assertThat(result!!.code).isEqualTo(ValidationCode.OFFENCE_DATE_OVER_OR_UNDER_100_YEARS_AGO)
      assertThat(result.message).isEqualTo("Court case 1 NOMIS line reference 1 offence date must be between ${LocalDate.now().minusYears(100).year} and ${LocalDate.now().year}")
    }

    @Test
    fun `returns ValidationMessage if offenceStartDate is more than 100 years in the future`() {
      val date = LocalDate.now().plusYears(101)
      val sentenceAndOffence = createSentenceAndOffence(date)
      val result = sentenceValidator.validateOffenceStartDateRange(sentenceAndOffence)
      assertThat(result).isNotNull()
      assertThat(result!!.code).isEqualTo(ValidationCode.OFFENCE_DATE_OVER_OR_UNDER_100_YEARS_AGO)
      assertThat(result.message).isEqualTo("Court case 1 NOMIS line reference 1 offence date must be between ${LocalDate.now().year} and ${LocalDate.now().plusYears(100).year}")
    }
  }

  @Nested
  inner class ValidateOffenceDateAfterSentenceDateTests {
    @Test
    fun `returns null if offenceStartDate is null`() {
      val sentenceAndOffence = createSentenceAndOffence(null)
      val result = sentenceValidator.validateOffenceDateAfterSentenceDate(sentenceAndOffence)
      assertThat(result).isNull()
    }

    @Test
    fun `returns null if offenceStartDate is before or equal to sentenceDate`() {
      val sentenceDate = LocalDate.now()
      val sentenceAndOffence = object : SentenceAndOffence by createSentenceAndOffence(sentenceDate.minusDays(1)) {
        override val sentenceDate = sentenceDate
      }
      val result = sentenceValidator.validateOffenceDateAfterSentenceDate(sentenceAndOffence)
      assertThat(result).isNull()

      val sentenceAndOffence2 = object : SentenceAndOffence by createSentenceAndOffence(sentenceDate) {
        override val sentenceDate = sentenceDate
      }
      val result2 = sentenceValidator.validateOffenceDateAfterSentenceDate(sentenceAndOffence2)
      assertThat(result2).isNull()
    }

    @Test
    fun `returns OFFENCE_DATE_AFTER_SENTENCE_START_DATE if offenceStartDate is after sentenceDate but within 100 years`() {
      val sentenceDate = LocalDate.now()
      val offenceStartDate = sentenceDate.plusDays(1)
      val sentenceAndOffence = object : SentenceAndOffence by createSentenceAndOffence(offenceStartDate) {
        override val sentenceDate = sentenceDate
      }
      val result = sentenceValidator.validateOffenceDateAfterSentenceDate(sentenceAndOffence)
      assertThat(result).isNotNull()
      assertThat(result!!.code).isEqualTo(ValidationCode.OFFENCE_DATE_AFTER_SENTENCE_START_DATE)
    }
  }
}
