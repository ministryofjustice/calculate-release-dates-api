package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.validator

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.mockito.Mockito.mock
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceAndOffenceWithReleaseArrangements
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.CalculationSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.OffenderOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonerDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationMessage
import java.time.LocalDate

class InvalidNomisOffenceCodeValidatorTest {

  private val validator = InvalidNomisOffenceCodeValidator()

  @ParameterizedTest
  @CsvSource(
    "SX56025-029N",
    "SX56087-088N",
    "PK78001-008NA",
    "SX56013-122N",
    "MD71230-245N",
    "MD71131-462N",
    "XX00000-017N",
    "CJ91015-034N",
    "SX56070-072N",
    "RT88000-002N",
    "MD71210-225N",
    "SX03035-038N",
    "XX00000-022N",
    "SX03007-008N",
    "SX03017-018N",
    "MD71130C-462CN",
    "PK78001-008N",
    "SX03001A-004AN",
    "FC81001-014N",
    "SX03000-001N",
    "SX56013A-019AN",
    "SX03013-014N",
    "PC00000-001N",
    "RT88000-001N",
    "SX56021-120N",
    "XX00000-014N",
    "MD71090-439N",
    "XX0106",
    "SX03005-006N",
    "TH68000-002N",
    "XX0113",
    "SX03031-044N",
    "XX0105",
    "SX03015-016N",
    "XX0112",
    "XX0104",
    "BA76000-001N",
    "COML025N",
    "CJ91000-001N",
    "MD71000-002N",
    "SX56014-074N",
    "XX0107",
    "TH68078-081N",
    "OF61000-003N",
    "SX03027-042N",
    "VG24001-047N",
    "XX00000-006N",
    "SX03019-022N",
    "SX03001-004N",
    "XX00000-019N",
    "XX0115",
    "XX0102",
    "SX03048-049N",
    "SX03062-175N",
    "XX0101",
    "XX0108",
    "TH68000-003N",
    "SX03045-046N",
    "XX00000-003N",
    "XX00000-020N",
    "XX00000-010N",
    "SX67002N",
    "COML020N",
    "XX00000-016N",
    "XX0103",
    "IA99000-001N",
    "XX00000-002N",
    "FY61000-001N",
    "XX00000-013N",
    "XX00000-011N",
    "COML022N",
    "SX03009-012N",
    "SX56039-118N",
    "RT88501-502N",
    "COML017N",
    "XX0114",
    "SX03070-077N",
    "TH68000-001N",
    "SX56037N",
  )
  fun `should reject sentences with an invalid offence code`(offenceCode: String) {
    val messages = validator.validate(
      CalculationSourceData(
        listOf(aSentenceForOffence(offenceCode = offenceCode)),
        prisonerDetails = PRISONER_DETAILS,
        bookingAndSentenceAdjustments = mock(),
        returnToCustodyDate = null,
      ),
    )
    val expectedMessage = ValidationMessage(ValidationCode.INVALID_NOMIS_OFFENCE_CODE, listOf(offenceCode, "Some offence description", " from case CASEABC123"))
    assertThat(messages).containsExactly(expectedMessage)
  }

  @Test
  fun `should format the message correctly when there is a case reference`() {
    val messages = validator.validate(
      CalculationSourceData(
        listOf(aSentenceForOffence(offenceCode = "XX123")),
        prisonerDetails = PRISONER_DETAILS,
        bookingAndSentenceAdjustments = mock(),
        returnToCustodyDate = null,
      ),
    )
    val expectedMessage = ValidationMessage(ValidationCode.INVALID_NOMIS_OFFENCE_CODE, listOf("XX123", "Some offence description", " from case CASEABC123"))
    assertThat(expectedMessage.message).isEqualTo("The offence XX123 Some offence description from case CASEABC123 is invalid.")
    assertThat(messages).containsExactly(expectedMessage)
  }

  @Test
  fun `should format the message correctly when there is no case reference`() {
    val messages = validator.validate(
      CalculationSourceData(
        listOf(aSentenceForOffence(offenceCode = "XX123").copy(caseReference = null)),
        prisonerDetails = PRISONER_DETAILS,
        bookingAndSentenceAdjustments = mock(),
        returnToCustodyDate = null,
      ),
    )
    val expectedMessage = ValidationMessage(ValidationCode.INVALID_NOMIS_OFFENCE_CODE, listOf("XX123", "Some offence description", ""))
    assertThat(expectedMessage.message).isEqualTo("The offence XX123 Some offence description is invalid.")
    assertThat(messages).containsExactly(expectedMessage)
  }

  @ParameterizedTest
  @CsvSource(
    "A123XX",
    "NA123456",
  )
  fun `should accept sentences containing valid offence codes`(offenceCode: String) {
    val messages = validator.validate(
      CalculationSourceData(
        listOf(aSentenceForOffence(offenceCode = offenceCode)),
        prisonerDetails = PRISONER_DETAILS,
        bookingAndSentenceAdjustments = mock(),
        returnToCustodyDate = null,
      ),
    )
    assertThat(messages).isEmpty()
  }

  private fun aSentenceForOffence(offenceCode: String) = A_SENTENCE.copy(offence = AN_OFFENCE.copy(offenceCode = offenceCode))

  companion object {
    private const val PRISONER_NUMBER = "A1234BC"
    private val PRISONER_DETAILS = PrisonerDetails(
      bookingId = 1,
      offenderNo = PRISONER_NUMBER,
      dateOfBirth = LocalDate.of(1980, 1, 1),
    )
    private val AN_OFFENCE = OffenderOffence(1L, LocalDate.of(2015, 1, 1), null, "ABC", "Some offence description", listOf("A"))
    private val A_SENTENCE = SentenceAndOffenceWithReleaseArrangements(
      bookingId = 999L,
      sentenceSequence = 1,
      lineSequence = 1,
      caseSequence = 1,
      sentenceDate = LocalDate.of(2022, 1, 1),
      sentenceStatus = "IMP",
      sentenceCategory = "CAT",
      sentenceCalculationType = SentenceCalculationType.ADIMP.name,
      sentenceTypeDescription = "ADMIP",
      terms = emptyList(),
      offence = AN_OFFENCE,
      caseReference = "CASEABC123",
      fineAmount = null,
      courtId = null,
      courtDescription = null,
      courtTypeCode = null,
      consecutiveToSequence = null,
      revocationDates = emptyList(),
    )
  }
}
