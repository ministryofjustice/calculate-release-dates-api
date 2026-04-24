package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.validator

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.adjustmentsapi.model.UnlawfullyAtLargeDto
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.LegislationName
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.SDSLegislation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.SDSLegislationConfiguration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType.UNLAWFULLY_AT_LARGE
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.TrancheName
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Adjustment
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AdjustmentAdditionalInfo
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Adjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationOutput
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Duration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offender
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SDSEarlyReleaseExclusionType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.StandardDeterminateSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationMessage
import java.time.LocalDate
import java.time.temporal.ChronoUnit.DAYS

class OutOfCustodyAtProgressionModelCommencementValidatorTest {

  private val progressionModelLegislation = mock<SDSLegislation.ProgressionModelLegislation>()
  private val sdsLegislationConfiguration = SDSLegislationConfiguration(
    defaultLegislation = mock(),
    sds40Legislation = mock(),
    sds40AdditionalExcludedOffencesLegislation = mock(),
    progressionModelLegislation = progressionModelLegislation,
  )

  private val validator = OutOfCustodyAtProgressionModelCommencementValidator(sdsLegislationConfiguration)

  private val calculationOutput = mock<CalculationOutput>()
  private val calculationResult = mock<CalculationResult>()

  @BeforeEach
  fun setUp() {
    whenever(progressionModelLegislation.commencementDate()).thenReturn(LocalDate.of(2026, 9, 2))
    whenever(calculationOutput.calculationResult).thenReturn(calculationResult)
  }

  @ParameterizedTest
  @CsvSource(
    // Sentence in absence / immigration detention return to custody before the day before commencement
    "2026-08-31,2026-08-31,SENTENCED_IN_ABSENCE,false",
    "2026-08-31,2026-08-31,IMMIGRATION_DETENTION,false",
    // Sentence in absence / immigration detention return to custody on the day before commencement
    "2026-08-31,2026-09-01,SENTENCED_IN_ABSENCE,true",
    "2026-08-31,2026-09-01,IMMIGRATION_DETENTION,true",
    // Sentence in absence / immigration detention leave and return to custody on the day before commencement
    "2026-09-01,2026-09-01,SENTENCED_IN_ABSENCE,true",
    "2026-09-01,2026-09-01,IMMIGRATION_DETENTION,true",
    // Sentence in absence / immigration detention leave and return to custody on the day of commencement
    "2026-09-02,2026-09-02,SENTENCED_IN_ABSENCE,false",
    "2026-09-02,2026-09-02,IMMIGRATION_DETENTION,false",
    // Release in error return to custody before the day before commencement
    "2026-08-31,2026-08-31,RELEASE_IN_ERROR,false",
    // Release in error return to custody on the day before commencement
    "2026-09-01,2026-09-01,RELEASE_IN_ERROR,true",
    // Release in error leave and return to custody on the day of commencement
    "2026-09-02,2026-09-02,RELEASE_IN_ERROR,true",
    // Release in error leave and return to custody on the day after commencement
    "2026-09-03,2026-09-03,RELEASE_IN_ERROR,false",
    // Escape on or after 2008-07-21 up to the day before commencement
    "2008-07-21,2026-09-01,ESCAPE,false",
    // Escape on the day before commencement and return on the day of commencement or later
    "2026-09-01,2026-09-02,ESCAPE,true",
    // Escape on the day of commencement and return on the day of commencement or later
    "2026-09-02,2026-09-02,ESCAPE,false",
    // Escape before 2008-07-21 and return before the day before commencement
    "2008-07-20,2026-08-31,ESCAPE,false",
    // Escape before 2008-07-21 and return on the day before commencement
    "2008-07-20,2026-09-01,ESCAPE,true",
    // Recall UAL spanning commencement is ignored
    "2026-08-15,2026-09-25,RECALL,false",
    // Unknown reason spanning commencement is ignored
    "2026-08-15,2026-09-25,,false",
  )
  fun `should be considered out of custody based on UAL`(from: LocalDate, to: LocalDate, type: UnlawfullyAtLargeDto.Type?, isOutOfCustody: Boolean) {
    whenever(calculationResult.trancheAllocationByLegislationName).thenReturn(
      mapOf(
        LegislationName.SDS_PROGRESSION_MODEL to TrancheName.TRANCHE_5,
      ),
    )
    val errors = validator.validate(
      calculationOutput,
      BOOKING.copy(adjustments = ualForDate(from, to, type)),
    )
    if (isOutOfCustody) {
      assertThat(errors).containsOnly(ValidationMessage(ValidationCode.UNSUPPORTED_OUT_OF_CUSTODY_AT_PROGRESSION_MODEL_COMMENCEMENT))
    } else {
      assertThat(errors).isEmpty()
    }
  }

  @Test
  fun `should be not be sent to manual if not assigned a progression model tranche for example if sentence is ineligible for PM`() {
    whenever(calculationResult.trancheAllocationByLegislationName).thenReturn(emptyMap())
    val errors = validator.validate(
      calculationOutput,
      BOOKING.copy(adjustments = ualForDate(LocalDate.of(2001, 1, 1), LocalDate.of(2030, 1, 1), UnlawfullyAtLargeDto.Type.ESCAPE)),
    )
    assertThat(errors).isEmpty()
  }

  private fun ualForDate(
    from: LocalDate,
    to: LocalDate,
    type: UnlawfullyAtLargeDto.Type?,
  ): Adjustments = Adjustments(
    mutableMapOf(
      UNLAWFULLY_AT_LARGE to mutableListOf(
        Adjustment(
          appliesToSentencesFrom = STANDARD_SENTENCE.sentencedAt,
          numberOfDays = DAYS.between(from, to).toInt(),
          fromDate = from,
          toDate = to,
          additionalInfo = AdjustmentAdditionalInfo.UALAdjustmentAdditionalInfo(type),
        ),
      ),
    ),
  )

  companion object {
    private val ONE_DAY_DURATION = Duration(mapOf(DAYS to 1L))
    private val OFFENCE = Offence(LocalDate.of(2020, 1, 1))
    private val STANDARD_SENTENCE = StandardDeterminateSentence(
      OFFENCE,
      ONE_DAY_DURATION,
      LocalDate.of(2020, 1, 1),
      isSDSPlus = false,
      hasAnSDSEarlyReleaseExclusion = SDSEarlyReleaseExclusionType.NO,
    )
    private val BOOKING = Booking(
      bookingId = 123456,
      returnToCustodyDate = null,
      offender = Offender(
        dateOfBirth = LocalDate.of(1980, 1, 1),
        reference = "A1234BC",
      ),
      sentences = mutableListOf(STANDARD_SENTENCE),
    )
  }
}
