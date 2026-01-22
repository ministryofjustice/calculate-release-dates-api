package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SDSEarlyReleaseExclusionType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceAndOffenceWithReleaseArrangements
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.CalculationSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.FixedTermRecallDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.OffenderOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonerDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.ReturnToCustodyDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceTerms
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi.PrisonApiExternalMovement
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.validator.FixedTermRecallValidator
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class RecallValidationFtr56ServiceTest {

  private val fixedTermRecallValidator = FixedTermRecallValidator()

  @Test
  fun `validate FTR56 sentence with historic ECSL or HDC does not trigger manually journey`() {
    val sentences = listOf(FTR_56_DAY_SENTENCE)
    val returnToCustodyDate = LocalDate.of(2024, 2, 1)

    val hdcExternalMovements = listOf(
      externalApiMovement,
      externalApiMovement.copy(directionCode = "OUT", movementReasonCode = "CR", movementDate = externalApiMovement.movementDate!!.minusDays(10)),
      externalApiMovement.copy(directionCode = "OUT", movementReasonCode = "HE", movementDate = externalApiMovement.movementDate.minusDays(11)),
    )

    val hdcMessages = fixedTermRecallValidator.validate(createSourceData(sentences, returnToCustodyDate, hdcExternalMovements))
    assertThat(hdcMessages).isEmpty()

    val ecslExternalMovements = listOf(
      externalApiMovement,
      externalApiMovement.copy(directionCode = "OUT", movementReasonCode = "CR", movementDate = externalApiMovement.movementDate.minusDays(10)),
      externalApiMovement.copy(directionCode = "OUT", movementReasonCode = "ECSL", movementDate = externalApiMovement.movementDate.minusDays(10)),
    )

    val eclsMessages = fixedTermRecallValidator.validate(createSourceData(sentences, returnToCustodyDate, ecslExternalMovements))
    assertThat(eclsMessages).isEmpty()
  }

  @Test
  fun `validate FTR56 sentence with ECSL or HDC prior to latest admission does not trigger manually journey`() {
    val sentences = listOf(FTR_56_DAY_SENTENCE)
    val returnToCustodyDate = LocalDate.of(2024, 2, 1)

    val hdcExternalMovements = listOf(
      externalApiMovement,
      externalApiMovement.copy(
        directionCode = "OUT",
        movementReasonCode = "CR",
        movementDate = externalApiMovement.movementDate!!.minusDays(10),
      ),
      externalApiMovement.copy(
        directionCode = "OUT",
        movementReasonCode = "HE",
        movementDate = externalApiMovement.movementDate.plusDays(1),
      ),
    )

    val messages =
      fixedTermRecallValidator.validate(createSourceData(sentences, returnToCustodyDate, hdcExternalMovements))
    assertThat(messages).isEmpty()
  }

  @Test
  fun `validate FTR56 sentence with no movements does not trigger manual journey`() {
    val sentences = listOf(FTR_56_DAY_SENTENCE)
    val returnToCustodyDate = LocalDate.of(2024, 2, 1)

    val messages = fixedTermRecallValidator.validate(createSourceData(sentences, returnToCustodyDate, emptyList()))
    assertThat(messages).isEmpty()
  }

  private fun createSourceData(
    sentences: List<SentenceAndOffenceWithReleaseArrangements>,
    returnToCustodyDate: LocalDate? = null,
    movements: List<PrisonApiExternalMovement>? = null,
  ) = CalculationSourceData(
    prisonerDetails = prisonerDetails,
    sentenceAndOffences = sentences,
    bookingAndSentenceAdjustments = org.mockito.kotlin.mock(),
    returnToCustodyDate = if (returnToCustodyDate != null) {
      ReturnToCustodyDate(
        returnToCustodyDate = returnToCustodyDate,
        bookingId = 1L,
      )
    } else {
      null
    },
    fixedTermRecallDetails = if (returnToCustodyDate != null) {
      FixedTermRecallDetails(
        returnToCustodyDate = returnToCustodyDate,
        bookingId = 1L,
        recallLength = 56,
      )
    } else {
      null
    },
    movements = movements ?: emptyList(),
  )

  private companion object {
    private val FTR_56_DAY_SENTENCE = SentenceAndOffenceWithReleaseArrangements(
      bookingId = 1L,
      sentenceSequence = 7,
      lineSequence = 1,
      caseSequence = 1,
      sentenceDate = LocalDate.of(2021, 1, 1),
      terms = listOf(
        SentenceTerms(5, 0, 0, 0, SentenceTerms.IMPRISONMENT_TERM_CODE),
      ),
      sentenceCalculationType = "FTR_56ORA",
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
      courtId = null,
      courtDescription = null,
      courtTypeCode = null,
      consecutiveToSequence = null,
      revocationDates = listOf(LocalDate.of(2024, 1, 1)),
      isSDSPlus = false,
      isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
      isSDSPlusOffenceInPeriod = false,
      hasAnSDSEarlyReleaseExclusion = SDSEarlyReleaseExclusionType.NO,
    )

    private val prisonerDetails = PrisonerDetails(
      bookingId = 1L,
      offenderNo = "ABC",
      dateOfBirth = LocalDate.of(1980, 1, 1),
    )

    private val externalApiMovement = PrisonApiExternalMovement(
      movementReasonCode = "",
      commentText = "",
      movementDate = LocalDate.now(),
      movementReason = "A",
      movementTime = LocalTime.now(),
      movementType = "A",
      directionCode = "IN",
      offenderNo = "ABC",
      movementTypeDescription = "ASD",
      createDateTime = LocalDateTime.now(),
      fromAgency = "xyz",
    )
  }
}
