package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.validator

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.FeatureToggles
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Duration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offender
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Recall
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RecallType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SDSEarlyReleaseExclusionType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SDSReleaseArrangements
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.StandardDeterminateSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationMessage
import java.time.LocalDate
import java.time.temporal.ChronoUnit.DAYS
import java.time.temporal.ChronoUnit.MONTHS
import java.time.temporal.ChronoUnit.WEEKS
import java.time.temporal.ChronoUnit.YEARS

class RouteProgressionModelExclusionToManualValidatorTest {

  private val featureToggles = FeatureToggles(routeProgressionModelScheduleExclusionToManual = true)
  private val validator = RouteProgressionModelExclusionToManualValidator(featureToggles)

  @Test
  fun `should accept sentence with exclusion if the feature toggle is off`() {
    featureToggles.routeProgressionModelScheduleExclusionToManual = false
    val result = validator.validate(MINIMAL_BOOKING.copy(sentences = listOf(INVALID_SDS)))
    assertThat(result).isEmpty()
  }

  @Test
  fun `should reject sentence with exclusion if the feature toggle is on`() {
    val result = validator.validate(MINIMAL_BOOKING.copy(sentences = listOf(INVALID_SDS)))
    assertThat(result).containsExactly(ValidationMessage(ValidationCode.PROGRESSION_MODEL_SCHEDULE_EXCLUSION))
  }

  @Test
  fun `should accept sentence with exclusion if it is also a recall`() {
    val sentence = INVALID_SDS.copy(recall = Recall(RecallType.FIXED_TERM_RECALL_56))
    val result = validator.validate(MINIMAL_BOOKING.copy(sentences = listOf(sentence)))
    assertThat(result).isEmpty()
  }

  @Test
  fun `should accept sentence with exclusion if it is also a S250`() {
    val sentence = INVALID_SDS.copy(
      releaseArrangements = SDSReleaseArrangements(
        isSDSPlus = false,
        isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
        sdsEarlyReleaseExclusions = listOf(SDSEarlyReleaseExclusionType.SA2026_PROGRESSION_MODEL_SCHEDULE),
        isSection250 = true,
      ),
    )
    val result = validator.validate(MINIMAL_BOOKING.copy(sentences = listOf(sentence)))
    assertThat(result).isEmpty()
  }

  companion object {
    private const val PRISONER_NUMBER = "A1234BC"
    private val MINIMAL_BOOKING = Booking(
      offender = Offender(
        reference = PRISONER_NUMBER,
        dateOfBirth = LocalDate.of(1982, 6, 15),
      ),
      sentences = emptyList(),
    )

    private val VALID_SDS = StandardDeterminateSentence(
      offence = Offence(LocalDate.of(2000, 1, 1)),
      duration = Duration(mutableMapOf(DAYS to 0L, WEEKS to 0L, MONTHS to 0L, YEARS to 5L)),
      sentencedAt = LocalDate.of(2000, 1, 1),
      releaseArrangements = SDSReleaseArrangements(
        isSDSPlus = false,
        isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
        sdsEarlyReleaseExclusions = emptyList(),
        isSection250 = false,
      ),
    )

    private val INVALID_SDS = StandardDeterminateSentence(
      offence = Offence(LocalDate.of(2001, 2, 3)),
      duration = Duration(mutableMapOf(DAYS to 0L, WEEKS to 0L, MONTHS to 6L, YEARS to 10L)),
      sentencedAt = LocalDate.of(2001, 2, 3),
      releaseArrangements = SDSReleaseArrangements(
        isSDSPlus = false,
        isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
        sdsEarlyReleaseExclusions = listOf(SDSEarlyReleaseExclusionType.SA2026_PROGRESSION_MODEL_SCHEDULE),
        isSection250 = false,
      ),
    )
  }
}
