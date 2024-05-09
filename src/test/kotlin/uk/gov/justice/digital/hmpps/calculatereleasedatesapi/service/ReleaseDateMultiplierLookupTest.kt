package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.CalculationParamsTestConfigHelper.releasePointMultiplierConfigurationForTests
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.ReleaseDateMultiplier
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.ReleasePointMultipliersConfiguration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceIdentificationTrack
import java.util.stream.Stream

class ReleaseDateMultiplierLookupTest {

  @Test
  fun `should use the multiplier for a track if present`() {
    val config = ReleasePointMultipliersConfiguration(
      listOf(ReleaseDateMultiplier(listOf(SentenceIdentificationTrack.SDS_STANDARD_RELEASE), 0.9)),
      0.45,
    )
    val releasePointMultiplierLookup = ReleasePointMultiplierLookup(config)
    assertThat(releasePointMultiplierLookup.multiplierFor(SentenceIdentificationTrack.SDS_STANDARD_RELEASE)).isEqualTo(0.9)
  }

  @Test
  fun `should use the default for a track that is not present`() {
    val config = ReleasePointMultipliersConfiguration(
      listOf(ReleaseDateMultiplier(listOf(SentenceIdentificationTrack.SDS_STANDARD_RELEASE), 0.9)),
      0.45,
    )
    val releasePointMultiplierLookup = ReleasePointMultiplierLookup(config)
    assertThat(releasePointMultiplierLookup.multiplierFor(SentenceIdentificationTrack.BOTUS)).isEqualTo(0.45)
  }

  @ParameterizedTest
  @MethodSource("multipliersForAllSentenceIdentificationTracks")
  fun `should return correct multiplier for track`(track: SentenceIdentificationTrack, expected: Double) {
    val productionLookup = ReleasePointMultiplierLookup(releasePointMultiplierConfigurationForTests())
    assertThat(productionLookup.multiplierFor(track)).isEqualTo(expected)
  }

  companion object {
    @JvmStatic
    private fun multipliersForAllSentenceIdentificationTracks(): Stream<Arguments> {
      // if you added a new track make sure you configure the release point multiplier or you will get the default
      // please don't add an else here as this will serve as a reminder to configure new tracks when it doesn't compile.
      return SentenceIdentificationTrack.entries.associateWith {
        when (it) {
          SentenceIdentificationTrack.SDS_BEFORE_CJA_LASPO -> 0.5
          SentenceIdentificationTrack.SDS_AFTER_CJA_LASPO -> 0.5
          SentenceIdentificationTrack.SDS_STANDARD_RELEASE -> 0.5
          SentenceIdentificationTrack.SDS_EARLY_RELEASE -> 0.5
          SentenceIdentificationTrack.SDS_TWO_THIRDS_RELEASE -> 0.66666
          SentenceIdentificationTrack.SDS_PLUS_RELEASE -> 0.66666
          SentenceIdentificationTrack.RECALL -> 0.5
          SentenceIdentificationTrack.EDS_AUTOMATIC_RELEASE -> 0.66666
          SentenceIdentificationTrack.EDS_DISCRETIONARY_RELEASE -> 1.00
          SentenceIdentificationTrack.SOPC_PED_AT_TWO_THIRDS -> 1.00
          SentenceIdentificationTrack.SOPC_PED_AT_HALFWAY -> 1.00
          SentenceIdentificationTrack.AFINE_ARD_AT_HALFWAY -> 0.5
          SentenceIdentificationTrack.AFINE_ARD_AT_FULL_TERM -> 1.00
          SentenceIdentificationTrack.DTO_BEFORE_PCSC -> 0.5
          SentenceIdentificationTrack.DTO_AFTER_PCSC -> 0.5
          SentenceIdentificationTrack.BOTUS -> 1.00
        }
      }.map { Arguments.of(it.key, it.value) }.stream()
    }
  }
}
