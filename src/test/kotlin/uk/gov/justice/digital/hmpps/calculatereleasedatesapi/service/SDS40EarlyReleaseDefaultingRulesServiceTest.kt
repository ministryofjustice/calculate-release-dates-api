package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.mock
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.SDS40TrancheConfiguration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.ApplicableLegislation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.EarlyReleaseSentenceFilter
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.PreLegislationCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.SDSLegislation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.TrancheConfiguration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.TrancheType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationRule
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceIdentificationTrack
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.TrancheName
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Adjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AffectedBySdsLegislation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationTrigger
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Duration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offender
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ReleaseDateCalculationBreakdown
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ReleaseDateTypes
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SDSReleaseArrangements
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceAdjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.StandardDeterminateSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.UnadjustedReleaseDate
import java.time.LocalDate
import java.time.Period
import java.time.temporal.ChronoUnit.DAYS
import java.time.temporal.ChronoUnit.MONTHS
import java.time.temporal.ChronoUnit.WEEKS
import java.time.temporal.ChronoUnit.YEARS
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class SDS40EarlyReleaseDefaultingRulesServiceTest {

  private val testCommencementDate = LocalDate.of(2024, 7, 29)
  private val testTrancheTwoCommencementDate = LocalDate.of(2024, 10, 22)
  private val testTrancheThreeCommencementDate = LocalDate.of(2024, 12, 16)

  private val earlyReleaseTrancheOne = TrancheConfiguration(
    date = testCommencementDate,
    type = TrancheType.SENTENCE_LENGTH_LESS_THAN,
    unit = YEARS,
    duration = 5,
    name = TrancheName.TRANCHE_1,
  )

  private val trancheConfiguration = SDS40TrancheConfiguration(
    testCommencementDate,
    testTrancheTwoCommencementDate,
    testTrancheThreeCommencementDate,
  )

  val sds40Legislation = SDSLegislation.SDS40Legislation(trancheConfiguration.getSds40Tranches(), mapOf(SentenceIdentificationTrack.SDS to ReleaseMultiplier.FORTY_PERCENT), EarlyReleaseSentenceFilter.SDS_40_EXCLUSIONS)

  private val service = SDS40EarlyReleaseDefaultingRulesService()

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
    )

    assertThat(
      service.applySDSEarlyReleaseRulesAndFinalizeDates(
        early,
        PreLegislationCalculation(standard, ApplicableLegislation(sds40Legislation, earlyReleaseTrancheOne.date)),
        createBookingWithSDSSentenceOfType(SentenceIdentificationTrack.SDS).sentences,
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
        affectedBySds40 = false,
      ),
    )
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
      mapOf(
        type to ReleaseDateCalculationBreakdown(
          emptySet(),
          releaseDate = LocalDate.of(2024, 7, 25),
          unadjustedDate = LocalDate.of(2024, 7, 25),
          affectedBySdsLegislation = AffectedBySdsLegislation(sds40 = true, progressionModel = false),
        ),
      ),
      emptyMap(),
      Period.ofYears(5),
    )

    val standard = CalculationResult(
      mapOf(type to LocalDate.of(2024, 8, 1)),
      mapOf(
        type to ReleaseDateCalculationBreakdown(
          emptySet(),
          releaseDate = LocalDate.of(2024, 7, 25),
          unadjustedDate = LocalDate.of(2024, 7, 25),
          affectedBySdsLegislation = AffectedBySdsLegislation(sds40 = false, progressionModel = false),
        ),
      ),
      emptyMap(),
      Period.ofYears(5),
    )

    assertThat(
      service.applySDSEarlyReleaseRulesAndFinalizeDates(
        early,
        PreLegislationCalculation(standard, ApplicableLegislation(sds40Legislation, earlyReleaseTrancheOne.date)),
        createBookingWithSDSSentenceOfType(SentenceIdentificationTrack.SDS).sentences,
      ),
    ).isEqualTo(
      CalculationResult(
        mapOf(type to testCommencementDate),
        mapOf(
          type to ReleaseDateCalculationBreakdown(
            setOf(CalculationRule.SDS_EARLY_RELEASE_ADJUSTED_TO_TRANCHE_1_COMMENCEMENT),
            releaseDate = testCommencementDate,
            unadjustedDate = LocalDate.of(2024, 7, 25),
            affectedBySdsLegislation = AffectedBySdsLegislation(sds40 = true, progressionModel = false),
          ),
        ),
        emptyMap(),
        Period.ofYears(5),
      ),
    )
  }

  @Test
  fun `Should apply the rules at a date type level so that early release is maintained after commencement even if other dates are adjusted`() {
    // should keep CRD & PED, adjust ERSED to 50% and adjust HDCED to commencement
    val early = CalculationResult(
      mapOf(
        ReleaseDateType.CRD to LocalDate.of(2024, 8, 9),
        ReleaseDateType.PED to LocalDate.of(2024, 8, 9),
        ReleaseDateType.ERSED to LocalDate.of(2024, 7, 25),
        ReleaseDateType.HDCED to LocalDate.of(2024, 7, 25),
      ),
      mapOf(
        ReleaseDateType.CRD to ReleaseDateCalculationBreakdown(
          emptySet(),
          releaseDate = LocalDate.of(2024, 8, 9),
          unadjustedDate = LocalDate.of(2024, 8, 9),
          affectedBySdsLegislation = AffectedBySdsLegislation(sds40 = true, progressionModel = false),
        ),
        ReleaseDateType.PED to ReleaseDateCalculationBreakdown(
          emptySet(),
          releaseDate = LocalDate.of(2024, 8, 9),
          unadjustedDate = LocalDate.of(2024, 8, 9),
          affectedBySdsLegislation = AffectedBySdsLegislation(sds40 = true, progressionModel = false),
        ),
        ReleaseDateType.ERSED to ReleaseDateCalculationBreakdown(
          emptySet(),
          releaseDate = LocalDate.of(2024, 7, 25),
          unadjustedDate = LocalDate.of(2024, 7, 25),
          affectedBySdsLegislation = AffectedBySdsLegislation(sds40 = true, progressionModel = false),
        ),
        ReleaseDateType.HDCED to ReleaseDateCalculationBreakdown(
          emptySet(),
          releaseDate = LocalDate.of(2024, 7, 25),
          unadjustedDate = LocalDate.of(2024, 7, 25),
          affectedBySdsLegislation = AffectedBySdsLegislation(sds40 = true, progressionModel = false),
        ),
      ),
      emptyMap(),
      Period.ofYears(5),
    )

    val standard = CalculationResult(
      mapOf(
        ReleaseDateType.CRD to LocalDate.of(2024, 8, 10),
        ReleaseDateType.PED to LocalDate.of(2024, 8, 10),
        ReleaseDateType.ERSED to LocalDate.of(2024, 7, 26),
        ReleaseDateType.HDCED to LocalDate.of(2024, 7, 30),
      ),
      mapOf(
        ReleaseDateType.CRD to ReleaseDateCalculationBreakdown(
          emptySet(),
          releaseDate = LocalDate.of(2024, 8, 10),
          unadjustedDate = LocalDate.of(2024, 8, 10),
          affectedBySdsLegislation = AffectedBySdsLegislation(sds40 = false, progressionModel = false),
        ),
        ReleaseDateType.PED to ReleaseDateCalculationBreakdown(
          emptySet(),
          releaseDate = LocalDate.of(2024, 8, 10),
          unadjustedDate = LocalDate.of(2024, 8, 10),
          affectedBySdsLegislation = AffectedBySdsLegislation(sds40 = false, progressionModel = false),
        ),
        ReleaseDateType.ERSED to ReleaseDateCalculationBreakdown(
          emptySet(),
          releaseDate = LocalDate.of(2024, 7, 26),
          unadjustedDate = LocalDate.of(2024, 7, 26),
          affectedBySdsLegislation = AffectedBySdsLegislation(sds40 = false, progressionModel = false),
        ),
        ReleaseDateType.HDCED to ReleaseDateCalculationBreakdown(
          emptySet(),
          releaseDate = LocalDate.of(2024, 7, 30),
          unadjustedDate = LocalDate.of(2024, 7, 30),
          affectedBySdsLegislation = AffectedBySdsLegislation(sds40 = false, progressionModel = false),
        ),
      ),
      emptyMap(),
      Period.ofYears(5),
    )

    assertThat(
      service.applySDSEarlyReleaseRulesAndFinalizeDates(
        early,
        PreLegislationCalculation(standard, ApplicableLegislation(sds40Legislation, earlyReleaseTrancheOne.date)),
        createBookingWithSDSSentenceOfType(SentenceIdentificationTrack.SDS).sentences,
      ),
    ).isEqualTo(
      CalculationResult(
        mapOf(
          ReleaseDateType.CRD to LocalDate.of(2024, 8, 9),
          ReleaseDateType.PED to LocalDate.of(2024, 8, 9),
          ReleaseDateType.ERSED to LocalDate.of(2024, 7, 26),
          ReleaseDateType.HDCED to testCommencementDate,
        ),
        mapOf(
          ReleaseDateType.CRD to ReleaseDateCalculationBreakdown(
            setOf(CalculationRule.SDS_EARLY_RELEASE_APPLIES),
            releaseDate = LocalDate.of(2024, 8, 9),
            unadjustedDate = LocalDate.of(2024, 8, 9),
            affectedBySdsLegislation = AffectedBySdsLegislation(sds40 = true, progressionModel = false),
          ),
          ReleaseDateType.PED to ReleaseDateCalculationBreakdown(
            setOf(CalculationRule.SDS_EARLY_RELEASE_APPLIES),
            releaseDate = LocalDate.of(2024, 8, 9),
            unadjustedDate = LocalDate.of(2024, 8, 9),
            affectedBySdsLegislation = AffectedBySdsLegislation(sds40 = true, progressionModel = false),
          ),
          ReleaseDateType.ERSED to ReleaseDateCalculationBreakdown(
            setOf(CalculationRule.SDS_STANDARD_RELEASE_APPLIES),
            releaseDate = LocalDate.of(2024, 7, 26),
            unadjustedDate = LocalDate.of(2024, 7, 26),
            affectedBySdsLegislation = AffectedBySdsLegislation(sds40 = false, progressionModel = false),
          ),
          ReleaseDateType.HDCED to ReleaseDateCalculationBreakdown(
            setOf(CalculationRule.SDS_EARLY_RELEASE_ADJUSTED_TO_TRANCHE_1_COMMENCEMENT),
            releaseDate = testCommencementDate,
            unadjustedDate = LocalDate.of(2024, 7, 25),
            affectedBySdsLegislation = AffectedBySdsLegislation(sds40 = true, progressionModel = false),
          ),
        ),
        emptyMap(),
        Period.ofYears(5),
      ),
    )
  }

  @Test
  fun `should correctly handle two sentences where one has a PED release date and is sentenced before the other`() {
    val early = CalculationResult(
      mapOf(ReleaseDateType.CRD to LocalDate.of(2024, 7, 25)),
      mapOf(
        ReleaseDateType.CRD to ReleaseDateCalculationBreakdown(
          emptySet(),
          releaseDate = LocalDate.of(2024, 7, 25),
          unadjustedDate = LocalDate.of(2024, 7, 25),
          affectedBySdsLegislation = AffectedBySdsLegislation(sds40 = true, progressionModel = false),
        ),
      ),
      emptyMap(),
      Period.ofYears(5),
    )

    val standard = CalculationResult(
      mapOf(ReleaseDateType.PED to LocalDate.of(2024, 8, 1)),
      mapOf(
        ReleaseDateType.PED to ReleaseDateCalculationBreakdown(
          emptySet(),
          releaseDate = LocalDate.of(2024, 8, 1),
          unadjustedDate = LocalDate.of(2024, 8, 1),
          affectedBySdsLegislation = AffectedBySdsLegislation(sds40 = false, progressionModel = false),
        ),
      ),
      emptyMap(),
      Period.ofYears(5),
    )

    assertThat(
      service.applySDSEarlyReleaseRulesAndFinalizeDates(
        early,
        PreLegislationCalculation(standard, ApplicableLegislation(sds40Legislation, earlyReleaseTrancheOne.date)),
        createBookingWithSDSSentenceOfType(
          SentenceIdentificationTrack.SDS,
          listOf(ReleaseDateType.CRD, ReleaseDateType.SLED),
        ).sentences,
      ),
    ).isEqualTo(
      CalculationResult(
        mapOf(ReleaseDateType.CRD to LocalDate.of(2024, 7, 25)),
        mapOf(
          ReleaseDateType.CRD to ReleaseDateCalculationBreakdown(
            setOf(CalculationRule.SDS_EARLY_RELEASE_APPLIES),
            releaseDate = LocalDate.of(2024, 7, 25),
            unadjustedDate = LocalDate.of(2024, 7, 25),
            affectedBySdsLegislation = AffectedBySdsLegislation(sds40 = true, progressionModel = false),
          ),
        ),
        emptyMap(),
        Period.ofYears(5),
      ),
    )
  }

  private fun createBookingWithSDSSentenceOfType(identificationTrack: SentenceIdentificationTrack, releaseDateTypes: List<ReleaseDateType>? = null): Booking {
    val sentence = StandardDeterminateSentence(
      sentencedAt = LocalDate.of(2020, 1, 1),
      duration = Duration(mutableMapOf(DAYS to 0L, WEEKS to 0L, MONTHS to 0L, YEARS to 5L)),
      offence = Offence(committedAt = LocalDate.of(2019, 1, 1)),
      identifier = UUID.randomUUID(),
      caseSequence = 1,
      lineSequence = 1,
      releaseArrangements = SDSReleaseArrangements(
        isSDSPlus = false,
        isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
        isSection250 = false,
        sdsEarlyReleaseExclusions = emptyList(),
      ),
    ).apply { releaseMultiplier = ReleaseMultiplier.ONE_HALF }
    sentence.identificationTrack = identificationTrack
    sentence.releaseDateTypes = ReleaseDateTypes(
      if (releaseDateTypes !== null) {
        releaseDateTypes
      } else {
        listOf(ReleaseDateType.CRD, ReleaseDateType.SLED, ReleaseDateType.PED)
      },
      sentence,
      offender = mock<Offender>(),
    )
    val sentenceCalculation = SentenceCalculation(
      UnadjustedReleaseDate(
        sentence,
        CalculationTrigger(LocalDate.now()),
      ),
      SentenceAdjustments(remand = 1),
      false,
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
    return booking
  }
}
