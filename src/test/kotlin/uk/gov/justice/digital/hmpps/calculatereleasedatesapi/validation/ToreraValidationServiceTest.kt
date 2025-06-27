package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SDSEarlyReleaseExclusionType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceAndOffenceWithReleaseArrangements
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.CalculationSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.OffenderOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceTerms
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.ImportantDates.SDS_DYO_TORERA_START_DATE
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.ImportantDates.SOPC_TORERA_END_DATE
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.ManageOffencesService
import java.time.LocalDate

class ToreraValidationServiceTest {
  private val manageOffencesService = mock<ManageOffencesService>()
  private val toreraValidationService = ToreraValidationService(manageOffencesService)

  @Test
  fun `Test validateToreraExempt returns SDS_TORERA_EXCLUSION exception with sentenced date after SDS_DYO_TORERA_START_DATE`() {
    val toreraOffenceCodes = listOf(
      toreraOffenceCode,
      "NOT_19ZA",
    )
    whenever(manageOffencesService.getToreraOffenceCodes()).thenReturn(toreraOffenceCodes)
    val result = toreraValidationService.validateToreraExempt(
      mock<CalculationSourceData> {
        on { sentenceAndOffences } doReturn listOf(validSDSSentence)
      },
    )

    assertThat(result.count()).isEqualTo(1)
    assertThat(result.first()).isEqualTo(ValidationMessage(ValidationCode.SDS_TORERA_EXCLUSION))
  }

  @Test
  fun `Test validateToreraExempt returns SOPC_TORERA_EXCLUSION exception with sentenced date prior to SOPC_TORERA_END_DATE`() {
    val toreraOffenceCodes = listOf(
      toreraOffenceCode,
      "NOT_19ZA",
    )
    whenever(manageOffencesService.getToreraOffenceCodes()).thenReturn(toreraOffenceCodes)
    val result = toreraValidationService.validateToreraExempt(
      mock<CalculationSourceData> {
        on { sentenceAndOffences } doReturn listOf(validSopcSentence)
      },
    )

    assertThat(result.count()).isEqualTo(1)
    assertThat(result.first()).isEqualTo(ValidationMessage(ValidationCode.SOPC_TORERA_EXCLUSION))
  }

  @Test
  fun `Test validateToreraExempt returns SDS_TORERA_EXCLUSION and SOPC_TORERA_EXCLUSION exception`() {
    val toreraOffenceCodes = listOf(
      toreraOffenceCode,
      "NOT_19ZA",
      "NOT_19ZA_ABC",
    )
    whenever(manageOffencesService.getToreraOffenceCodes()).thenReturn(toreraOffenceCodes)

    val result = toreraValidationService.validateToreraExempt(
      mock<CalculationSourceData> {
        on { sentenceAndOffences } doReturn listOf(validSDSSentence, validSopcSentence)
      },
    )

    assertThat(result.count()).isEqualTo(2)
    assertThat(result).isEqualTo(
      listOf(
        ValidationMessage(ValidationCode.SDS_TORERA_EXCLUSION),
        ValidationMessage(ValidationCode.SOPC_TORERA_EXCLUSION),
      ),
    )
  }

  @Test
  fun `Test validateToreraExempt does not trigger for SDS sentence with no offence codes in schedule 19ZA`() {
    val toreraOffenceCodes = listOf("NOT_19ZA")
    whenever(manageOffencesService.getToreraOffenceCodes()).thenReturn(toreraOffenceCodes)
    val result = toreraValidationService.validateToreraExempt(
      mock<CalculationSourceData> {
        on { sentenceAndOffences } doReturn listOf(validSDSSentence)
      },
    )
    assertThat(result.count()).isEqualTo(0)
  }

  @Test
  fun `Test validateToreraExempt does not trigger for SOPC sentence with no offence codes in schedule 19ZA`() {
    val toreraOffenceCodes = listOf("NOT_19ZA")
    whenever(manageOffencesService.getToreraOffenceCodes()).thenReturn(toreraOffenceCodes)
    val result = toreraValidationService.validateToreraExempt(
      mock<CalculationSourceData> {
        on { sentenceAndOffences } doReturn listOf(validSopcSentence)
      },
    )
    assertThat(result.count()).isEqualTo(0)
  }

  @Test
  fun `Test validateToreraExempt does not trigger for SDS sentence with sentenced date on before SDS_DYO_TORERA_START_DATE`() {
    val toreraOffenceCodes = listOf("NOT_19ZA", toreraOffenceCode)
    whenever(manageOffencesService.getToreraOffenceCodes()).thenReturn(toreraOffenceCodes)
    val result = toreraValidationService.validateToreraExempt(
      mock<CalculationSourceData> {
        on { sentenceAndOffences } doReturn listOf(
          validSDSSentence.copy(sentenceDate = SDS_DYO_TORERA_START_DATE),
          validSDSSentence.copy(sentenceDate = SDS_DYO_TORERA_START_DATE.minusDays(1)),
        )
      },
    )
    assertThat(result.count()).isEqualTo(0)
  }

  @Test
  fun `Test validateToreraExempt does not trigger for SOPC sentence with sentenced date on or after 28-06-2022`() {
    val toreraOffenceCodes = listOf(toreraOffenceCode)
    whenever(manageOffencesService.getToreraOffenceCodes()).thenReturn(toreraOffenceCodes)
    val result = toreraValidationService.validateToreraExempt(
      mock<CalculationSourceData> {
        on { sentenceAndOffences } doReturn listOf(
          validSopcSentence.copy(sentenceDate = SOPC_TORERA_END_DATE),
          validSopcSentence.copy(sentenceDate = SOPC_TORERA_END_DATE.plusDays(1)),
        )
      },
    )
    assertThat(result.count()).isEqualTo(0)
  }

  companion object {
    private val lineSequence = 154
    private val caseSequence = 155
    private val toreraOffenceCode = "A123456"

    private val validSDSSentence = SentenceAndOffenceWithReleaseArrangements(
      bookingId = 1,
      sentenceSequence = 1,
      sentenceDate = SDS_DYO_TORERA_START_DATE.plusDays(1),
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
      offence = OffenderOffence(
        offenderChargeId = 111,
        offenceStartDate = LocalDate.parse("2021-01-01"),
        offenceCode = toreraOffenceCode,
        offenceDescription = "Littering",
      ),
      lineSequence = lineSequence,
      caseSequence = caseSequence,
      caseReference = null,
      fineAmount = null,
      courtDescription = null,
      consecutiveToSequence = null,
      isSDSPlus = false,
      isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
      isSDSPlusOffenceInPeriod = false,
      hasAnSDSEarlyReleaseExclusion = SDSEarlyReleaseExclusionType.NO,
    )

    private val validSopcSentence = validSDSSentence.copy(
      sentenceCalculationType = SentenceCalculationType.SOPC21.name,
      sentenceDate = SOPC_TORERA_END_DATE.minusDays(1),
      sentenceTypeDescription = "Offender of Particular Concern",
    )
  }
}
