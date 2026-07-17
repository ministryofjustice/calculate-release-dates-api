package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.EnumSource
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.OffenderOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceTerms
import java.time.LocalDate

class SDSMetaDataTest {

  @ParameterizedTest
  @EnumSource(SDSEarlyReleaseExclusionType::class)
  fun `should provide display name texts based on exclusions`(exclusion: SDSEarlyReleaseExclusionType) {
    val expected = when (exclusion) {
      SDSEarlyReleaseExclusionType.SEXUAL -> SDSDescriptions("Sexual", null, null)
      SDSEarlyReleaseExclusionType.VIOLENT -> SDSDescriptions("Violent", null, null)
      SDSEarlyReleaseExclusionType.DOMESTIC_ABUSE -> SDSDescriptions("Domestic Abuse", null, null)
      SDSEarlyReleaseExclusionType.NATIONAL_SECURITY -> SDSDescriptions("National Security", null, null)
      SDSEarlyReleaseExclusionType.TERRORISM -> SDSDescriptions("Terrorism", null, null)
      SDSEarlyReleaseExclusionType.SEXUAL_T3 -> SDSDescriptions("Sexual (for prisoners in custody on or after the 16th Dec 2024)", null, null)
      SDSEarlyReleaseExclusionType.DOMESTIC_ABUSE_T3 -> SDSDescriptions("Domestic Abuse (for prisoners in custody on or after the 16th Dec 2024)", null, null)
      SDSEarlyReleaseExclusionType.MURDER_T3 -> SDSDescriptions("Murder (for prisoners in custody on or after the 16th Dec 2024)", null, null)
      SDSEarlyReleaseExclusionType.PROGRESSION_MODEL_SCHEDULE_13_PART_3 -> SDSDescriptions(null, "Schedule 13 Part 3", null)
      SDSEarlyReleaseExclusionType.PROGRESSION_MODEL_OTHER_THING -> SDSDescriptions(null, "Other thing", null)
      SDSEarlyReleaseExclusionType.NO -> SDSDescriptions(null, null, null)
    }
    val result = SDSDescriptions.from(
      ADIMP_SENTENCE.copy(
        sdsReleaseArrangements = SDSReleaseArrangements(
          isSDSPlus = false,
          isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
          sdsEarlyReleaseExclusions = listOf(exclusion),
          isSection250 = false,
        ),
      ),
    )
    assertThat(result).isEqualTo(expected)
  }

  @ParameterizedTest
  @CsvSource("SEC91_03", "SEC91_03_ORA")
  fun `should provide progression model exclusion if it's a section 91`(sentenceCalculationType: String) {
    val result = SDSDescriptions.from(
      ADIMP_SENTENCE.copy(sentenceCalculationType = sentenceCalculationType),
    )
    assertThat(result).isEqualTo(SDSDescriptions(null, "Section 91", null))
  }

  @ParameterizedTest
  @CsvSource("SEC250", "SEC250_ORA")
  fun `should provide progression model exclusion if it's a section 250`(sentenceCalculationType: String) {
    val result = SDSDescriptions.from(
      ADIMP_SENTENCE.copy(sentenceCalculationType = sentenceCalculationType),
    )
    assertThat(result).isEqualTo(SDSDescriptions(null, "Section 250", null))
  }

  @ParameterizedTest
  @CsvSource(
    "ADIMP,Would be SDS+",
    "ADIMP_ORA,Would be SDS+",
    "YOI,Would be YOI+",
    "YOI_ORA,Would be YOI+",
  )
  fun `should provide progression model exclusion if it would be SDS+`(sentenceCalculationType: String, expected: String) {
    val result = SDSDescriptions.from(
      ADIMP_SENTENCE.copy(
        sentenceCalculationType = sentenceCalculationType,
        sdsReleaseArrangements = SDSReleaseArrangements(
          isSDSPlus = false,
          isSDSPlusEligibleSentenceTypeLengthAndOffence = true,
          sdsEarlyReleaseExclusions = emptyList(),
          isSection250 = false,
        ),
      ),
    )
    assertThat(result).isEqualTo(SDSDescriptions(null, expected, null))
  }

  @ParameterizedTest
  @CsvSource(
    "ADIMP,SDS+",
    "ADIMP_ORA,SDS+",
    "YOI,YOI+",
    "YOI_ORA,YOI+",
    "SEC250,S250+",
    "SEC250_ORA,S250+",
  )
  fun `should provide correct SDS+ display name`(sentenceCalculationType: String, expected: String) {
    val result = SDSDescriptions.from(
      ADIMP_SENTENCE.copy(
        sentenceCalculationType = sentenceCalculationType,
        sdsReleaseArrangements = SDSReleaseArrangements(
          isSDSPlus = true,
          isSDSPlusEligibleSentenceTypeLengthAndOffence = true,
          sdsEarlyReleaseExclusions = emptyList(),
          isSection250 = false,
        ),
      ),
    )
    assertThat(result?.sdsPlusDisplayName).isEqualTo(expected)
  }

  @Test
  fun `should have no exclusions`() {
    val result = SDSDescriptions.from(ADIMP_SENTENCE)
    assertThat(result).isEqualTo(SDSDescriptions(null, null, null))
  }

  @Test
  fun `should return null if not SDS`() {
    val result = SDSDescriptions.from(ADIMP_SENTENCE.copy(sentenceCalculationType = "SOPC21"))
    assertThat(result).isNull()
  }

  companion object {
    private val ADIMP_SENTENCE = SentenceAndOffenceWithReleaseArrangements(
      bookingId = 1L,
      sentenceSequence = 1,
      lineSequence = 1,
      caseSequence = 1,
      sentenceDate = LocalDate.of(2012, 1, 1),
      terms = listOf(
        SentenceTerms(years = 5),
      ),
      sentenceStatus = "A",
      sentenceCategory = "SEN",
      sentenceCalculationType = "ADIMP",
      sentenceTypeDescription = "DESC",
      offence = OffenderOffence(
        123,
        LocalDate.of(2012, 1, 1),
        LocalDate.of(2012, 1, 1),
        "AB123DEF",
        "finagling",
        emptyList(),
      ),
      caseReference = null,
      fineAmount = null,
      courtId = null,
      courtDescription = null,
      courtTypeCode = null,
      consecutiveToSequence = null,
      sdsReleaseArrangements = SDSReleaseArrangements(
        isSDSPlus = false,
        isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
        sdsEarlyReleaseExclusions = emptyList(),
        isSection250 = false,
      ),
    )
  }
}
