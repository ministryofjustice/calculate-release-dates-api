package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.SDS40TrancheConfiguration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationRule
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SDSEarlyReleaseTranche
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceIdentificationTrack
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Adjustment
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Adjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Duration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offender
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RecallType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ReleaseDateCalculationBreakdown
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ReleaseDateTypes
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SDSEarlyReleaseExclusionType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.StandardDeterminateSentence
import java.time.LocalDate
import java.time.Period
import java.time.temporal.ChronoUnit.DAYS
import java.time.temporal.ChronoUnit.MONTHS
import java.time.temporal.ChronoUnit.WEEKS
import java.time.temporal.ChronoUnit.YEARS
import java.util.*

@ExtendWith(MockitoExtension::class)
class SDSEarlyReleaseDefaultingRulesServiceTest {

  private val testCommencementDate = LocalDate.of(2024, 7, 29)
  private val testTrancheTwoCommencementDate = LocalDate.of(2024, 10, 22)

  @Mock
  private val trancheConfiguration = SDS40TrancheConfiguration(testCommencementDate, testTrancheTwoCommencementDate)

  private val service = SDSEarlyReleaseDefaultingRulesService(SentencesExtractionService(), trancheConfiguration)

  @Test
  fun `should not require recalculation if no SDS early release`() {
    val booking = createBookingWithSDSSentenceOfType(SentenceIdentificationTrack.SDS_STANDARD_RELEASE)
    assertThat(service.hasAnySDSEarlyRelease(booking)).isFalse()
  }

  @Test
  fun `should require recalculation if any single type requires recalc`() {
    val booking = createBookingWithSDSSentenceOfType(SentenceIdentificationTrack.SDS_EARLY_RELEASE)
    assertThat(service.hasAnySDSEarlyRelease(booking)).isTrue()
  }

  @ParameterizedTest
  @CsvSource(
    "CRD",
    "PED",
    "ERSED",
    "HDCED",
  )
  fun `should use standard release point if that is also before commencement and take it's breakdown`(type: ReleaseDateType) {
    val early = CalculationResult(
      mapOf(type to LocalDate.of(2024, 7, 25)),
      mapOf(
        type to ReleaseDateCalculationBreakdown(),
      ),
      emptyMap(),
      Period.ofYears(5),
      sdsEarlyReleaseTranche = SDSEarlyReleaseTranche.TRANCHE_0,
    )

    val standard = CalculationResult(
      mapOf(type to LocalDate.of(2024, 7, 26)),
      mapOf(
        type to ReleaseDateCalculationBreakdown(
          setOf(CalculationRule.HDCED_ADJUSTED_TO_CONCURRENT_CONDITIONAL_RELEASE),
        ),
      ),
      emptyMap(),
      Period.ofYears(5),
      sdsEarlyReleaseTranche = SDSEarlyReleaseTranche.TRANCHE_0,
    )

    assertThat(
      service.applySDSEarlyReleaseRulesAndFinalizeDates(
        early,
        standard,
        testCommencementDate,
        SDSEarlyReleaseTranche.TRANCHE_0,
        createBookingWithSDSSentenceOfType(SentenceIdentificationTrack.SDS_EARLY_RELEASE),
      ),
    ).isEqualTo(
      CalculationResult(
        mapOf(type to LocalDate.of(2024, 7, 26)),
        mapOf(
          type to ReleaseDateCalculationBreakdown(
            setOf(CalculationRule.HDCED_ADJUSTED_TO_CONCURRENT_CONDITIONAL_RELEASE, CalculationRule.SDS_STANDARD_RELEASE_APPLIES),
          ),
        ),
        emptyMap(),
        Period.ofYears(5),
        sdsEarlyReleaseTranche = SDSEarlyReleaseTranche.TRANCHE_0,
        affectedBySds40 = false,
      ),
    )
  }

  @Test
  fun `should take original TUSED and breakdown if recall CRD is before Tranche 1 Commencement`() {
    val testBreakdown = ReleaseDateCalculationBreakdown()

    val standard = CalculationResult(
      mapOf(ReleaseDateType.TUSED to LocalDate.of(2024, 8, 1)),
      mapOf(
        ReleaseDateType.TUSED to testBreakdown,
      ),
      emptyMap(),
      Period.ofYears(5),
      sdsEarlyReleaseTranche = SDSEarlyReleaseTranche.TRANCHE_1,
    )

    val testOffender = Offender("a", LocalDate.of(1980, 1, 1), true)

    val sentence = StandardDeterminateSentence(
      sentencedAt = LocalDate.of(2020, 1, 1),
      duration = Duration(mutableMapOf(DAYS to 0L, WEEKS to 0L, MONTHS to 0L, YEARS to 5L)),
      offence = Offence(committedAt = LocalDate.of(2019, 1, 1)),
      identifier = UUID.randomUUID(),
      caseSequence = 1,
      lineSequence = 1,
      isSDSPlus = false,
      hasAnSDSEarlyReleaseExclusion = SDSEarlyReleaseExclusionType.NO,
      recallType = RecallType.STANDARD_RECALL,
    )
    sentence.releaseDateTypes = ReleaseDateTypes(listOf(ReleaseDateType.TUSED), sentence, testOffender)

    val date = LocalDate.of(2024, 1, 1)
    val sentenceCalculation = SentenceCalculation(
      sentence,
      3,
      4.0,
      4,
      4,
      date,
      date,
      unadjustedDeterminateReleaseDate = testCommencementDate.minusDays(1),
      1,
      date,
      false,
      Adjustments(
        mutableMapOf(AdjustmentType.REMAND to mutableListOf(Adjustment(numberOfDays = 1, appliesToSentencesFrom = date))),
      ),
      date,
      date,
    )

    sentence.sentenceCalculation = sentenceCalculation
    sentence.identificationTrack = SentenceIdentificationTrack.RECALL
    val recallBooking = Booking(
      testOffender,
      listOf(
        sentence,
      ),
      Adjustments(),
      null,
      null,
      123,
    )

    recallBooking.consecutiveSentences = emptyList()

    val resultDates = mutableMapOf<ReleaseDateType, LocalDate>(ReleaseDateType.TUSED to LocalDate.now())
    val resultBreakdown = mutableMapOf<ReleaseDateType, ReleaseDateCalculationBreakdown>()
    service.handleTUSEDForSDSRecallsBeforeTrancheOneCommencement(resultDates, recallBooking, standard, resultBreakdown)

    assertThat(resultDates[ReleaseDateType.TUSED]).isEqualTo(LocalDate.of(2024, 8, 1))
    assertThat(resultBreakdown[ReleaseDateType.TUSED]).isEqualTo(testBreakdown)
  }

  @Test
  fun `should retain existing TUSED and breakdown if recall CRD is after Tranche 1 Commencement`() {
    val testBreakdown = ReleaseDateCalculationBreakdown()

    val standard = CalculationResult(
      mapOf(ReleaseDateType.TUSED to LocalDate.of(2024, 11, 1)),
      mapOf(
        ReleaseDateType.TUSED to testBreakdown,
      ),
      emptyMap(),
      Period.ofYears(5),
      sdsEarlyReleaseTranche = SDSEarlyReleaseTranche.TRANCHE_1,
    )

    val testOffender = Offender("a", LocalDate.of(1980, 1, 1), true)

    val sentence = StandardDeterminateSentence(
      sentencedAt = LocalDate.of(2020, 1, 1),
      duration = Duration(mutableMapOf(DAYS to 0L, WEEKS to 0L, MONTHS to 0L, YEARS to 5L)),
      offence = Offence(committedAt = LocalDate.of(2019, 1, 1)),
      identifier = UUID.randomUUID(),
      caseSequence = 1,
      lineSequence = 1,
      isSDSPlus = false,
      hasAnSDSEarlyReleaseExclusion = SDSEarlyReleaseExclusionType.NO,
      recallType = RecallType.STANDARD_RECALL,
    )
    sentence.releaseDateTypes = ReleaseDateTypes(listOf(ReleaseDateType.TUSED), sentence, testOffender)

    val date = LocalDate.of(2024, 1, 1)
    val sentenceCalculation = SentenceCalculation(
      sentence,
      3,
      4.0,
      4,
      4,
      date,
      date,
      unadjustedDeterminateReleaseDate = testCommencementDate.plusDays(1),
      1,
      date,
      false,
      Adjustments(
        mutableMapOf(AdjustmentType.REMAND to mutableListOf(Adjustment(numberOfDays = 1, appliesToSentencesFrom = date))),
      ),
      date,
      date,
    )

    sentence.sentenceCalculation = sentenceCalculation
    sentence.identificationTrack = SentenceIdentificationTrack.RECALL
    val recallBooking = Booking(
      testOffender,
      listOf(
        sentence,
      ),
      Adjustments(),
      null,
      null,
      123,
    )

    recallBooking.consecutiveSentences = emptyList()

    val resultDates = mutableMapOf<ReleaseDateType, LocalDate>(ReleaseDateType.TUSED to date)
    val resultBreakdown = mutableMapOf<ReleaseDateType, ReleaseDateCalculationBreakdown>()
    service.handleTUSEDForSDSRecallsBeforeTrancheOneCommencement(resultDates, recallBooking, standard, resultBreakdown)

    assertThat(resultDates[ReleaseDateType.TUSED]).isEqualTo(date)
    assertThat(resultBreakdown).doesNotContainKeys(ReleaseDateType.TUSED)
  }

  @ParameterizedTest
  @CsvSource(
    "CRD",
    "PED",
    "ERSED",
    "HDCED",
  )
  fun `should use commencement point if the standard is after commencement`(type: ReleaseDateType) {
    val early = CalculationResult(
      mapOf(type to LocalDate.of(2024, 7, 25)),
      emptyMap(),
      emptyMap(),
      Period.ofYears(5),
      sdsEarlyReleaseTranche = SDSEarlyReleaseTranche.TRANCHE_0,
      affectedBySds40 = true,
    )

    val standard = CalculationResult(
      mapOf(type to LocalDate.of(2024, 8, 1)),
      emptyMap(),
      emptyMap(),
      Period.ofYears(5),
      sdsEarlyReleaseTranche = SDSEarlyReleaseTranche.TRANCHE_1,
      affectedBySds40 = true,
    )

    assertThat(
      service.applySDSEarlyReleaseRulesAndFinalizeDates(
        early,
        standard,
        testCommencementDate,
        SDSEarlyReleaseTranche.TRANCHE_1,
        createBookingWithSDSSentenceOfType(SentenceIdentificationTrack.SDS_EARLY_RELEASE),
      ),
    ).isEqualTo(
      CalculationResult(
        mapOf(type to testCommencementDate),
        mapOf(
          type to ReleaseDateCalculationBreakdown(
            setOf(CalculationRule.SDS_EARLY_RELEASE_ADJUSTED_TO_TRANCHE_1_COMMENCEMENT),
            releaseDate = testCommencementDate,
            unadjustedDate = LocalDate.of(2024, 7, 25),
          ),
        ),
        emptyMap(),
        Period.ofYears(5),
        sdsEarlyReleaseAllocatedTranche = SDSEarlyReleaseTranche.TRANCHE_1,
        sdsEarlyReleaseTranche = SDSEarlyReleaseTranche.TRANCHE_1,
        affectedBySds40 = true,
      ),
    )
  }

  @Test
  fun `Should apply the rules at a date type level so that early release is maintained after commencement even if other dates are adjusted`() {
    // should keep CRD & PED, adjust ERSED to 50% and adjust HDCED to commencement
    val early = CalculationResult(
      mapOf(
        ReleaseDateType.CRD to LocalDate.of(2024, 8, 10),
        ReleaseDateType.PED to LocalDate.of(2024, 8, 10),
        ReleaseDateType.ERSED to LocalDate.of(2024, 7, 25),
        ReleaseDateType.HDCED to LocalDate.of(2024, 7, 25),
      ),
      emptyMap(),
      emptyMap(),
      Period.ofYears(5),
      sdsEarlyReleaseTranche = SDSEarlyReleaseTranche.TRANCHE_1,
      affectedBySds40 = true,
    )

    val standard = CalculationResult(
      mapOf(
        ReleaseDateType.CRD to LocalDate.of(2024, 8, 10),
        ReleaseDateType.PED to LocalDate.of(2024, 8, 10),
        ReleaseDateType.ERSED to LocalDate.of(2024, 7, 26),
        ReleaseDateType.HDCED to LocalDate.of(2024, 7, 30),
      ),
      emptyMap(),
      emptyMap(),
      Period.ofYears(5),
      sdsEarlyReleaseTranche = SDSEarlyReleaseTranche.TRANCHE_1,
      affectedBySds40 = true,
    )

    assertThat(
      service.applySDSEarlyReleaseRulesAndFinalizeDates(
        early,
        standard,
        testCommencementDate,
        SDSEarlyReleaseTranche.TRANCHE_1,
        createBookingWithSDSSentenceOfType(SentenceIdentificationTrack.SDS_EARLY_RELEASE),
      ),
    ).isEqualTo(
      CalculationResult(
        mapOf(
          ReleaseDateType.CRD to LocalDate.of(2024, 8, 10),
          ReleaseDateType.PED to LocalDate.of(2024, 8, 10),
          ReleaseDateType.ERSED to LocalDate.of(2024, 7, 26),
          ReleaseDateType.HDCED to testCommencementDate,
        ),
        mapOf(
          ReleaseDateType.HDCED to ReleaseDateCalculationBreakdown(
            setOf(CalculationRule.SDS_EARLY_RELEASE_ADJUSTED_TO_TRANCHE_1_COMMENCEMENT),
            releaseDate = testCommencementDate,
            unadjustedDate = LocalDate.of(2024, 7, 25),
          ),
        ),
        emptyMap(),
        Period.ofYears(5),
        sdsEarlyReleaseAllocatedTranche = SDSEarlyReleaseTranche.TRANCHE_1,
        sdsEarlyReleaseTranche = SDSEarlyReleaseTranche.TRANCHE_1,
        affectedBySds40 = true,
      ),
    )
  }

  private fun createBookingWithSDSSentenceOfType(identificationTrack: SentenceIdentificationTrack): Booking {
    val sentence = StandardDeterminateSentence(
      sentencedAt = LocalDate.of(2020, 1, 1),
      duration = Duration(mutableMapOf(DAYS to 0L, WEEKS to 0L, MONTHS to 0L, YEARS to 5L)),
      offence = Offence(committedAt = LocalDate.of(2019, 1, 1)),
      identifier = UUID.randomUUID(),
      caseSequence = 1,
      lineSequence = 1,
      isSDSPlus = false,
      hasAnSDSEarlyReleaseExclusion = SDSEarlyReleaseExclusionType.NO,
    )
    sentence.identificationTrack = identificationTrack
    val date = LocalDate.of(2024, 1, 1)
    val sentenceCalculation = SentenceCalculation(
      sentence,
      3,
      4.0,
      4,
      4,
      date,
      date,
      unadjustedDeterminateReleaseDate = testCommencementDate.plusDays(1),
      1,
      date,
      false,
      Adjustments(
        mutableMapOf(AdjustmentType.REMAND to mutableListOf(Adjustment(numberOfDays = 1, appliesToSentencesFrom = date))),
      ),
      date,
      date,
    )
    sentence.sentenceCalculation = sentenceCalculation

    val booking = Booking(
      Offender("a", LocalDate.of(1980, 1, 1), true),
      listOf(
        sentence,
      ),
      Adjustments(),
      null,
      null,
      123,
    )
    booking.consecutiveSentences = emptyList()
    return booking
  }
}
