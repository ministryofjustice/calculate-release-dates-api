package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationRule
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceIdentificationTrack
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Adjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Duration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offender
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ReleaseDateCalculationBreakdown
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SDSEarlyReleaseExclusionType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.StandardDeterminateSentence
import java.time.LocalDate
import java.time.Period
import java.time.temporal.ChronoUnit.DAYS
import java.time.temporal.ChronoUnit.MONTHS
import java.time.temporal.ChronoUnit.WEEKS
import java.time.temporal.ChronoUnit.YEARS
import java.util.*

class SDSEarlyReleaseDefaultingRulesServiceTest {

  private val testCommencementDate = LocalDate.of(2024, 7, 29)
  private val service = SDSEarlyReleaseDefaultingRulesService(testCommencementDate)

  @Test
  fun `should not require recalculation if no SDS early release`() {
    val booking = createBookingWithSDSSentenceOfType(SentenceIdentificationTrack.SDS_STANDARD_RELEASE)
    val result = CalculationResult(
      mapOf(ReleaseDateType.CRD to LocalDate.of(2020, 1, 1)),
      emptyMap(),
      emptyMap(),
      Period.ofYears(5),
    )
    assertThat(service.requiresRecalculation(booking, result)).isFalse()
  }

  @ParameterizedTest
  @CsvSource(
    "CRD,2024-07-28,true",
    "CRD,2024-07-29,false",
    "CRD,2024-07-30,false",
    "ERSED,2024-07-28,true",
    "ERSED,2024-07-29,false",
    "ERSED,2024-07-30,false",
    "HDCED,2024-07-28,true",
    "HDCED,2024-07-29,false",
    "HDCED,2024-07-30,false",
    "PED,2024-07-28,true",
    "PED,2024-07-29,false",
    "PED,2024-07-30,false",
  )
  fun `should require recalculation only if SDS early release and date is before commencement date`(type: ReleaseDateType, date: LocalDate, requiresRecalc: Boolean) {
    val booking = createBookingWithSDSSentenceOfType(SentenceIdentificationTrack.SDS_EARLY_RELEASE)
    val result = CalculationResult(
      mapOf(type to date),
      emptyMap(),
      emptyMap(),
      Period.ofYears(5),
    )
    assertThat(service.requiresRecalculation(booking, result)).isEqualTo(requiresRecalc)
  }

  @Test
  fun `should require recalculation if any single type requires recalc`() {
    val booking = createBookingWithSDSSentenceOfType(SentenceIdentificationTrack.SDS_EARLY_RELEASE)
    val result = CalculationResult(
      mapOf(
        ReleaseDateType.CRD to LocalDate.of(2024, 7, 29),
        ReleaseDateType.PED to LocalDate.of(2024, 1, 29),
        ReleaseDateType.ERSED to LocalDate.of(2024, 1, 29),
        ReleaseDateType.HDCED to LocalDate.of(2020, 1, 1),
      ),
      emptyMap(),
      emptyMap(),
      Period.ofYears(5),
    )
    assertThat(service.requiresRecalculation(booking, result)).isTrue()
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
        type to ReleaseDateCalculationBreakdown(
          setOf(CalculationRule.CONSECUTIVE_SENTENCE_HDCED_CALCULATION),
        ),
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

    assertThat(service.mergeResults(early, standard)).isEqualTo(
      CalculationResult(
        mapOf(type to LocalDate.of(2024, 7, 26)),
        mapOf(
          type to ReleaseDateCalculationBreakdown(
            setOf(CalculationRule.HDCED_ADJUSTED_TO_CONCURRENT_CONDITIONAL_RELEASE),
          ),
        ),
        emptyMap(),
        Period.ofYears(5),
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
      emptyMap(),
      emptyMap(),
      Period.ofYears(5),
    )

    val standard = CalculationResult(
      mapOf(type to LocalDate.of(2024, 8, 1)),
      emptyMap(),
      emptyMap(),
      Period.ofYears(5),
    )

    assertThat(service.mergeResults(early, standard)).isEqualTo(
      CalculationResult(
        mapOf(type to testCommencementDate),
        mapOf(
          type to ReleaseDateCalculationBreakdown(
            setOf(CalculationRule.SDS_EARLY_RELEASE_ADJUSTED_TO_TRANCHE_COMMENCEMENT),
            releaseDate = testCommencementDate,
            unadjustedDate = LocalDate.of(2024, 7, 25),
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
        ReleaseDateType.CRD to LocalDate.of(2024, 8, 10),
        ReleaseDateType.PED to LocalDate.of(2024, 8, 10),
        ReleaseDateType.ERSED to LocalDate.of(2024, 7, 25),
        ReleaseDateType.HDCED to LocalDate.of(2024, 7, 25),
      ),
      emptyMap(),
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
      emptyMap(),
      emptyMap(),
      Period.ofYears(5),
    )

    assertThat(service.mergeResults(early, standard)).isEqualTo(
      CalculationResult(
        mapOf(
          ReleaseDateType.CRD to LocalDate.of(2024, 8, 10),
          ReleaseDateType.PED to LocalDate.of(2024, 8, 10),
          ReleaseDateType.ERSED to LocalDate.of(2024, 7, 26),
          ReleaseDateType.HDCED to testCommencementDate,
        ),
        mapOf(
          ReleaseDateType.HDCED to ReleaseDateCalculationBreakdown(
            setOf(CalculationRule.SDS_EARLY_RELEASE_ADJUSTED_TO_TRANCHE_COMMENCEMENT),
            releaseDate = testCommencementDate,
            unadjustedDate = LocalDate.of(2024, 7, 25),
          ),
        ),
        emptyMap(),
        Period.ofYears(5),
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
    return Booking(
      Offender("a", LocalDate.of(1980, 1, 1), true),
      listOf(
        sentence,
      ),
      Adjustments(),
      null,
      null,
      123,
    )
  }
}
