package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.validator

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.SDSLegislation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.SDSLegislationConfiguration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationOutput
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ExternalMovement
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ExternalMovementDirection
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ExternalMovementReason
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offender
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode
import java.time.LocalDate
import java.time.Period

@ExtendWith(MockitoExtension::class)
class HdcedRepealValidatorTest {

  @Mock
  lateinit var sdsLegislationConfiguration: SDSLegislationConfiguration

  @Mock
  lateinit var progressionModelLegislation: SDSLegislation.ProgressionModelLegislation

  private lateinit var validator: HdcedRepealValidator

  private val hdcedDate = LocalDate.of(2026, 5, 10)
  private val offender = Offender("A1234AA", LocalDate.of(1980, 1, 1))
  private val calculationResult = CalculationResult(
    dates = mapOf(ReleaseDateType.HDCED to hdcedDate),
    effectiveSentenceLength = Period.of(1, 1, 1),
  )
  private val calculationResultNoHdced = CalculationResult(
    dates = emptyMap(),
    effectiveSentenceLength = Period.of(1, 1, 1),
  )

  @BeforeEach
  fun setUp() {
    validator = HdcedRepealValidator(sdsLegislationConfiguration)
  }

  @Test
  fun `should return no messages when progression model legislation is inactive`() {
    whenever(sdsLegislationConfiguration.progressionModelLegislation).thenReturn(null)
    val calculationOutput = CalculationOutput(
      calculationResult = calculationResult,
      sentences = emptyList(),
      sentenceGroup = emptyList(),
    )
    val booking = Booking(offender = offender, sentences = emptyList(), externalMovements = emptyList())

    val messages = validator.validate(calculationOutput, booking)

    assertThat(messages).isEmpty()
  }

  @Test
  fun `should return no messages when calculation output does not contain HDCED`() {
    whenever(sdsLegislationConfiguration.progressionModelLegislation).thenReturn(progressionModelLegislation)
    val calculationOutput = CalculationOutput(
      calculationResult = calculationResultNoHdced,
      sentences = emptyList(),
      sentenceGroup = emptyList(),
    )
    val booking = Booking(offender = offender, sentences = emptyList(), externalMovements = emptyList())

    val messages = validator.validate(calculationOutput, booking)

    assertThat(messages).isEmpty()
  }

  @Test
  fun `should return no messages when there are no previous HDC releases`() {
    whenever(sdsLegislationConfiguration.progressionModelLegislation).thenReturn(progressionModelLegislation)
    val calculationOutput = CalculationOutput(
      calculationResult = calculationResult,
      sentences = emptyList(),
      sentenceGroup = emptyList(),
    )
    val booking = Booking(
      offender = offender,
      sentences = emptyList(),
      externalMovements = listOf(
        ExternalMovement(
          movementDate = LocalDate.of(2025, 1, 1),
          direction = ExternalMovementDirection.OUT,
          movementReason = ExternalMovementReason.PAROLE,
        ),
      ),
    )

    val messages = validator.validate(calculationOutput, booking)

    assertThat(messages).isEmpty()
  }

  @Test
  fun `should return no messages when prisoner was returned to custody after last HDC release`() {
    whenever(sdsLegislationConfiguration.progressionModelLegislation).thenReturn(progressionModelLegislation)
    val lastHdcReleaseDate = LocalDate.of(2025, 1, 1)
    val calculationOutput = CalculationOutput(
      calculationResult = calculationResult,
      sentences = emptyList(),
      sentenceGroup = emptyList(),
    )
    val booking = Booking(
      offender = offender,
      sentences = emptyList(),
      externalMovements = listOf(
        ExternalMovement(
          movementDate = lastHdcReleaseDate,
          direction = ExternalMovementDirection.OUT,
          movementReason = ExternalMovementReason.HDC,
        ),
        ExternalMovement(
          movementDate = lastHdcReleaseDate.plusDays(10),
          direction = ExternalMovementDirection.IN,
          movementReason = ExternalMovementReason.RECALL_ADMISSION,
        ),
      ),
    )

    val messages = validator.validate(calculationOutput, booking)

    assertThat(messages).isEmpty()
  }

  @Test
  fun `should return validation message when prisoner was not returned to custody after last HDC release`() {
    whenever(sdsLegislationConfiguration.progressionModelLegislation).thenReturn(progressionModelLegislation)
    val lastHdcReleaseDate = LocalDate.of(2025, 1, 1)
    val calculationOutput = CalculationOutput(
      calculationResult = calculationResult,
      sentences = emptyList(),
      sentenceGroup = emptyList(),
    )
    val booking = Booking(
      offender = offender,
      sentences = emptyList(),
      externalMovements = listOf(
        ExternalMovement(
          movementDate = lastHdcReleaseDate,
          direction = ExternalMovementDirection.OUT,
          movementReason = ExternalMovementReason.HDC,
        ),
      ),
    )

    val messages = validator.validate(calculationOutput, booking)

    assertThat(messages).hasSize(1)
    with(messages[0]) {
      assertThat(code).isEqualTo(ValidationCode.HDCED_REPEAL)
      assertThat(arguments).containsExactly(hdcedDate.toString())
    }
  }

  @Test
  fun `should correctly identify the last HDC release among multiple movements`() {
    whenever(sdsLegislationConfiguration.progressionModelLegislation).thenReturn(progressionModelLegislation)
    val firstHdcReleaseDate = LocalDate.of(2024, 5, 1)
    val latestHdcReleaseDate = LocalDate.of(2025, 1, 1)
    val calculationOutput = CalculationOutput(
      calculationResult = calculationResult,
      sentences = emptyList(),
      sentenceGroup = emptyList(),
    )
    val booking = Booking(
      offender = offender,
      sentences = emptyList(),
      externalMovements = listOf(
        ExternalMovement(
          movementDate = firstHdcReleaseDate,
          direction = ExternalMovementDirection.OUT,
          movementReason = ExternalMovementReason.HDC,
        ),
        ExternalMovement(
          movementDate = firstHdcReleaseDate.plusDays(5),
          direction = ExternalMovementDirection.IN,
          movementReason = ExternalMovementReason.RECALL_ADMISSION,
        ),
        ExternalMovement(
          movementDate = latestHdcReleaseDate,
          direction = ExternalMovementDirection.OUT,
          movementReason = ExternalMovementReason.HDC,
        ),
      ),
    )

    val messages = validator.validate(calculationOutput, booking)

    assertThat(messages).hasSize(1)
    with(messages[0]) {
      assertThat(code).isEqualTo(ValidationCode.HDCED_REPEAL)
      assertThat(arguments).containsExactly(hdcedDate.toString())
    }
  }

  @Test
  fun `should return no messages when IN movement is on the same day as the last HDC release`() {
    whenever(sdsLegislationConfiguration.progressionModelLegislation).thenReturn(progressionModelLegislation)
    val hdcMovementDate = LocalDate.of(2025, 1, 1)
    val calculationOutput = CalculationOutput(
      calculationResult = calculationResult,
      sentences = emptyList(),
      sentenceGroup = emptyList(),
    )
    val booking = Booking(
      offender = offender,
      sentences = emptyList(),
      externalMovements = listOf(
        ExternalMovement(
          movementDate = hdcMovementDate,
          direction = ExternalMovementDirection.OUT,
          movementReason = ExternalMovementReason.HDC,
        ),
        ExternalMovement(
          movementDate = hdcMovementDate, // Same day recall
          direction = ExternalMovementDirection.IN,
          movementReason = ExternalMovementReason.RECALL_ADMISSION,
        ),
      ),
    )

    val messages = validator.validate(calculationOutput, booking)

    assertThat(messages).isEmpty()
  }
}
