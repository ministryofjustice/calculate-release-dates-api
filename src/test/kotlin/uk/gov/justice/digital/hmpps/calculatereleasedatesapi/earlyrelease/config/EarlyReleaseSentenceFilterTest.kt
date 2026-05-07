package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceIdentificationTrack
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Duration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SDSEarlyReleaseExclusionType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SDSReleaseArrangements
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.StandardDeterminateSentence
import java.time.LocalDate
import java.time.temporal.ChronoUnit.DAYS
import java.time.temporal.ChronoUnit.MONTHS
import java.time.temporal.ChronoUnit.WEEKS
import java.time.temporal.ChronoUnit.YEARS

class EarlyReleaseSentenceFilterTest {

  @Test
  fun `SDS 40 should exclude SDS plus`() {
    val sentence = StandardDeterminateSentence(
      sentencedAt = LocalDate.of(2000, 1, 1),
      duration = FIVE_YEAR_DURATION,
      offence = Offence(
        committedAt = LocalDate.of(2000, 1, 1),
        offenceCode = "123",
      ),
      releaseArrangements = SDSReleaseArrangements(
        isSDSPlus = true,
        isSDSPlusEligibleSentenceTypeLengthAndOffence = true,
        isSection250 = false,
        sdsEarlyReleaseExclusions = emptyList(),
      ),
    )
    sentence.identificationTrack = SentenceIdentificationTrack.SDS_PLUS
    assertThat(EarlyReleaseSentenceFilter.SDS_40_EXCLUSIONS.isIncluded(sentence)).isFalse
  }

  @Test
  fun `SDS 40 should include section 250`() {
    val sentence = StandardDeterminateSentence(
      sentencedAt = LocalDate.of(2000, 1, 1),
      duration = FIVE_YEAR_DURATION,
      offence = Offence(
        committedAt = LocalDate.of(2000, 1, 1),
        offenceCode = "123",
      ),
      releaseArrangements = SDSReleaseArrangements(
        isSDSPlus = false,
        isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
        isSection250 = true,
        sdsEarlyReleaseExclusions = emptyList(),
      ),
    )
    sentence.identificationTrack = SentenceIdentificationTrack.SDS
    assertThat(EarlyReleaseSentenceFilter.SDS_40_EXCLUSIONS.isIncluded(sentence)).isTrue
  }

  @ParameterizedTest
  @EnumSource(SDSEarlyReleaseExclusionType::class)
  fun `SDS 40 exclusions should exclude only original SDS 40 early release exclusions`(exclusion: SDSEarlyReleaseExclusionType) {
    val sentence = StandardDeterminateSentence(
      sentencedAt = LocalDate.of(2000, 1, 1),
      duration = FIVE_YEAR_DURATION,
      offence = Offence(
        committedAt = LocalDate.of(2000, 1, 1),
        offenceCode = "123",
      ),
      releaseArrangements = SDSReleaseArrangements(
        isSDSPlus = false,
        isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
        isSection250 = false,
        sdsEarlyReleaseExclusions = listOf(exclusion),
      ),
    )
    sentence.identificationTrack = SentenceIdentificationTrack.SDS

    val isIncluded = EarlyReleaseSentenceFilter.SDS_40_EXCLUSIONS.isIncluded(sentence)
    val expected = when (exclusion) {
      SDSEarlyReleaseExclusionType.SEXUAL -> false
      SDSEarlyReleaseExclusionType.VIOLENT -> false
      SDSEarlyReleaseExclusionType.DOMESTIC_ABUSE -> false
      SDSEarlyReleaseExclusionType.NATIONAL_SECURITY -> false
      SDSEarlyReleaseExclusionType.TERRORISM -> false
      SDSEarlyReleaseExclusionType.SEXUAL_T3 -> true
      SDSEarlyReleaseExclusionType.DOMESTIC_ABUSE_T3 -> true
      SDSEarlyReleaseExclusionType.MURDER_T3 -> true
      SDSEarlyReleaseExclusionType.PROGRESSION_MODEL_SCHEDULE_13_PART_3 -> true
      SDSEarlyReleaseExclusionType.NO -> true
    }
    assertThat(isIncluded).describedAs("isIncluded expected to be $expected for $exclusion").isEqualTo(expected)
  }

  @ParameterizedTest
  @EnumSource(SDSEarlyReleaseExclusionType::class)
  fun `SDS 40 Additional Excluded Offences should include only original the additional early release exclusions`(exclusion: SDSEarlyReleaseExclusionType) {
    val sentence = StandardDeterminateSentence(
      sentencedAt = LocalDate.of(2000, 1, 1),
      duration = FIVE_YEAR_DURATION,
      offence = Offence(
        committedAt = LocalDate.of(2000, 1, 1),
        offenceCode = "123",
      ),
      releaseArrangements = SDSReleaseArrangements(
        isSDSPlus = false,
        isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
        isSection250 = false,
        sdsEarlyReleaseExclusions = listOf(exclusion),
      ),
    )
    sentence.identificationTrack = SentenceIdentificationTrack.SDS

    val isIncluded = EarlyReleaseSentenceFilter.SDS_40_ADDITIONAL_EXCLUDED_OFFENCES.isIncluded(sentence)
    val expected = when (exclusion) {
      SDSEarlyReleaseExclusionType.SEXUAL -> false
      SDSEarlyReleaseExclusionType.VIOLENT -> false
      SDSEarlyReleaseExclusionType.DOMESTIC_ABUSE -> false
      SDSEarlyReleaseExclusionType.NATIONAL_SECURITY -> false
      SDSEarlyReleaseExclusionType.TERRORISM -> false
      SDSEarlyReleaseExclusionType.SEXUAL_T3 -> true
      SDSEarlyReleaseExclusionType.DOMESTIC_ABUSE_T3 -> true
      SDSEarlyReleaseExclusionType.MURDER_T3 -> true
      SDSEarlyReleaseExclusionType.PROGRESSION_MODEL_SCHEDULE_13_PART_3 -> false
      SDSEarlyReleaseExclusionType.NO -> false
    }
    assertThat(isIncluded).describedAs("isIncluded expected to be $expected for $exclusion").isEqualTo(expected)
  }

  @Test
  fun `SDS progression model should include SDS plus`() {
    val sentence = StandardDeterminateSentence(
      sentencedAt = LocalDate.of(2000, 1, 1),
      duration = FIVE_YEAR_DURATION,
      offence = Offence(
        committedAt = LocalDate.of(2000, 1, 1),
        offenceCode = "123",
      ),
      releaseArrangements = SDSReleaseArrangements(
        isSDSPlus = true,
        isSDSPlusEligibleSentenceTypeLengthAndOffence = true,
        isSection250 = false,
        sdsEarlyReleaseExclusions = emptyList(),
      ),
    )
    sentence.identificationTrack = SentenceIdentificationTrack.SDS_PLUS
    assertThat(EarlyReleaseSentenceFilter.SDS_PROGRESSION_MODEL.isIncluded(sentence)).isTrue
  }

  @Test
  fun `SDS progression model should exclude section 250`() {
    val sentence = StandardDeterminateSentence(
      sentencedAt = LocalDate.of(2000, 1, 1),
      duration = FIVE_YEAR_DURATION,
      offence = Offence(
        committedAt = LocalDate.of(2000, 1, 1),
        offenceCode = "123",
      ),
      releaseArrangements = SDSReleaseArrangements(
        isSDSPlus = false,
        isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
        isSection250 = true,
        sdsEarlyReleaseExclusions = emptyList(),
      ),
    )
    sentence.identificationTrack = SentenceIdentificationTrack.SDS
    assertThat(EarlyReleaseSentenceFilter.SDS_PROGRESSION_MODEL.isIncluded(sentence)).isFalse
  }

  @ParameterizedTest
  @EnumSource(SDSEarlyReleaseExclusionType::class)
  fun `SDS Progression Model should exclude only Schedule 13 Part 3`(exclusion: SDSEarlyReleaseExclusionType) {
    val sentence = StandardDeterminateSentence(
      sentencedAt = LocalDate.of(2000, 1, 1),
      duration = FIVE_YEAR_DURATION,
      offence = Offence(
        committedAt = LocalDate.of(2000, 1, 1),
        offenceCode = "123",
      ),
      releaseArrangements = SDSReleaseArrangements(
        isSDSPlus = false,
        isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
        isSection250 = false,
        sdsEarlyReleaseExclusions = listOf(exclusion),
      ),
    )
    sentence.identificationTrack = SentenceIdentificationTrack.SDS

    val isIncluded = EarlyReleaseSentenceFilter.SDS_PROGRESSION_MODEL.isIncluded(sentence)
    val expected = when (exclusion) {
      SDSEarlyReleaseExclusionType.SEXUAL -> true
      SDSEarlyReleaseExclusionType.VIOLENT -> true
      SDSEarlyReleaseExclusionType.DOMESTIC_ABUSE -> true
      SDSEarlyReleaseExclusionType.NATIONAL_SECURITY -> true
      SDSEarlyReleaseExclusionType.TERRORISM -> true
      SDSEarlyReleaseExclusionType.SEXUAL_T3 -> true
      SDSEarlyReleaseExclusionType.DOMESTIC_ABUSE_T3 -> true
      SDSEarlyReleaseExclusionType.MURDER_T3 -> true
      SDSEarlyReleaseExclusionType.PROGRESSION_MODEL_SCHEDULE_13_PART_3 -> false
      SDSEarlyReleaseExclusionType.NO -> true
    }
    assertThat(isIncluded).describedAs("isIncluded expected to be $expected for $exclusion").isEqualTo(expected)
  }

  companion object {
    private val FIVE_YEAR_DURATION = Duration(mutableMapOf(DAYS to 0L, WEEKS to 0L, MONTHS to 0L, YEARS to 5L))
  }
}
