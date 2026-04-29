package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SDSReleaseArrangementsTest {

  @Test
  fun `empty list means no exclusions`() {
    val arrangements = SDSReleaseArrangements(
      isSDSPlus = false,
      isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
      sdsEarlyReleaseExclusions = emptyList(),
      isSection250 = false,
    )
    assertThat(arrangements.hasSDS40EarlyReleaseExclusion()).describedAs("hasSDS40EarlyReleaseExclusion").isFalse()
    assertThat(arrangements.hasSDS40AdditionalExcludedOffencesExclusion()).describedAs("hasSDS40AdditionalExcludedOffencesExclusion").isFalse()
    assertThat(arrangements.hasProgressionModelExclusion()).describedAs("hasProgressionModelExclusion").isFalse()
  }

  @Test
  fun `only NO in the list means no exclusions even though it should never be empty, old calcs may contain just NO`() {
    val arrangements = SDSReleaseArrangements(
      isSDSPlus = false,
      isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
      sdsEarlyReleaseExclusions = listOf(SDSEarlyReleaseExclusionType.NO),
      isSection250 = false,
    )
    assertThat(arrangements.hasSDS40EarlyReleaseExclusion()).describedAs("hasSDS40EarlyReleaseExclusion").isFalse()
    assertThat(arrangements.hasSDS40AdditionalExcludedOffencesExclusion()).describedAs("hasSDS40AdditionalExcludedOffencesExclusion").isFalse()
    assertThat(arrangements.hasProgressionModelExclusion()).describedAs("hasProgressionModelExclusion").isFalse()
  }

  @Test
  fun `Single exclusion for SDS40 is only an SDS40 exclusion`() {
    val arrangements = SDSReleaseArrangements(
      isSDSPlus = false,
      isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
      sdsEarlyReleaseExclusions = listOf(SDSEarlyReleaseExclusionType.SEXUAL),
      isSection250 = false,
    )
    assertThat(arrangements.hasSDS40EarlyReleaseExclusion()).describedAs("hasSDS40EarlyReleaseExclusion").isTrue()
    assertThat(arrangements.hasSDS40AdditionalExcludedOffencesExclusion()).describedAs("hasSDS40AdditionalExcludedOffencesExclusion").isFalse()
    assertThat(arrangements.hasProgressionModelExclusion()).describedAs("hasProgressionModelExclusion").isFalse()
  }

  @Test
  fun `Single exclusion for SDS40 AEO is only an SDS40 AEO exclusion`() {
    val arrangements = SDSReleaseArrangements(
      isSDSPlus = false,
      isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
      sdsEarlyReleaseExclusions = listOf(SDSEarlyReleaseExclusionType.SEXUAL_T3),
      isSection250 = false,
    )
    assertThat(arrangements.hasSDS40EarlyReleaseExclusion()).describedAs("hasSDS40EarlyReleaseExclusion").isFalse()
    assertThat(arrangements.hasSDS40AdditionalExcludedOffencesExclusion()).describedAs("hasSDS40AdditionalExcludedOffencesExclusion").isTrue()
    assertThat(arrangements.hasProgressionModelExclusion()).describedAs("hasProgressionModelExclusion").isFalse()
  }

  @Test
  fun `Single exclusion for progression model is only a progression model exclusion`() {
    val arrangements = SDSReleaseArrangements(
      isSDSPlus = false,
      isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
      sdsEarlyReleaseExclusions = listOf(SDSEarlyReleaseExclusionType.SCHEDULE_13_PART_3),
      isSection250 = false,
    )
    assertThat(arrangements.hasSDS40EarlyReleaseExclusion()).describedAs("hasSDS40EarlyReleaseExclusion").isFalse()
    assertThat(arrangements.hasSDS40AdditionalExcludedOffencesExclusion()).describedAs("hasSDS40AdditionalExcludedOffencesExclusion").isFalse()
    assertThat(arrangements.hasProgressionModelExclusion()).describedAs("hasProgressionModelExclusion").isTrue()
  }

  @Test
  fun `Multiple exclusions for SDS40 and Progression Model have the correct exclusions`() {
    val arrangements = SDSReleaseArrangements(
      isSDSPlus = false,
      isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
      sdsEarlyReleaseExclusions = listOf(SDSEarlyReleaseExclusionType.SEXUAL, SDSEarlyReleaseExclusionType.SCHEDULE_13_PART_3),
      isSection250 = false,
    )
    assertThat(arrangements.hasSDS40EarlyReleaseExclusion()).describedAs("hasSDS40EarlyReleaseExclusion").isTrue()
    assertThat(arrangements.hasSDS40AdditionalExcludedOffencesExclusion()).describedAs("hasSDS40AdditionalExcludedOffencesExclusion").isFalse()
    assertThat(arrangements.hasProgressionModelExclusion()).describedAs("hasProgressionModelExclusion").isTrue()
  }

  @Test
  fun `Multiple exclusions for SDS40 AEO and Progression Model have the correct exclusions`() {
    val arrangements = SDSReleaseArrangements(
      isSDSPlus = false,
      isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
      sdsEarlyReleaseExclusions = listOf(SDSEarlyReleaseExclusionType.SEXUAL_T3, SDSEarlyReleaseExclusionType.SCHEDULE_13_PART_3),
      isSection250 = false,
    )
    assertThat(arrangements.hasSDS40EarlyReleaseExclusion()).describedAs("hasSDS40EarlyReleaseExclusion").isFalse()
    assertThat(arrangements.hasSDS40AdditionalExcludedOffencesExclusion()).describedAs("hasSDS40AdditionalExcludedOffencesExclusion").isTrue()
    assertThat(arrangements.hasProgressionModelExclusion()).describedAs("hasProgressionModelExclusion").isTrue()
  }
}
