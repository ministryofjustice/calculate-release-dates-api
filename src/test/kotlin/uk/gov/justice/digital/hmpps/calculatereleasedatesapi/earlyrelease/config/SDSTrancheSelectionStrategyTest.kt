package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.SDSTrancheSelectionStrategy.SDS40TrancheSelectionStrategy
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.SDSTrancheSelectionStrategy.SDSProgressionModelTrancheSelectionStrategy
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.TrancheName
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.stream.Stream

class SDSTrancheSelectionStrategyTest {

  @ParameterizedTest
  @MethodSource(value = ["allSdsStrategies"])
  fun `should return true if all durations are less than the tranche duration`(strategy: SDSTrancheSelectionStrategy) {
    val trancheConfig = TrancheConfiguration(
      type = TrancheType.SENTENCE_LENGTH,
      date = LocalDate.now(),
      duration = 4 * 365,
      unit = ChronoUnit.DAYS,
      name = TrancheName.TRANCHE_1,
    )
    val durations = listOf(365L, 730L)
    assertThat(strategy.sentenceDurationsWithinTrancheDuration(trancheConfig, durations)).isTrue
  }

  @ParameterizedTest
  @MethodSource(value = ["allSdsStrategies"])
  fun `should return false if any duration is equal to the tranche duration`(strategy: SDSTrancheSelectionStrategy) {
    val trancheConfig = TrancheConfiguration(
      type = TrancheType.SENTENCE_LENGTH,
      date = LocalDate.now(),
      duration = 4 * 365,
      unit = ChronoUnit.DAYS,
      name = TrancheName.TRANCHE_1,
    )
    val durations = listOf(365L, 1460L)
    assertThat(strategy.sentenceDurationsWithinTrancheDuration(trancheConfig, durations)).isFalse
  }

  @ParameterizedTest
  @MethodSource(value = ["allSdsStrategies"])
  fun `should return false if any duration is greater than the tranche duration`(strategy: SDSTrancheSelectionStrategy) {
    val trancheConfig = TrancheConfiguration(
      type = TrancheType.SENTENCE_LENGTH,
      date = LocalDate.now(),
      duration = 4 * 365,
      unit = ChronoUnit.DAYS,
      name = TrancheName.TRANCHE_1,
    )
    val durations = listOf(365L, 2000L)
    assertThat(strategy.sentenceDurationsWithinTrancheDuration(trancheConfig, durations)).isFalse
  }

  @ParameterizedTest
  @MethodSource(value = ["allSdsStrategies"])
  fun `should return false if tranche duration is null`(strategy: SDSTrancheSelectionStrategy) {
    val trancheConfig = TrancheConfiguration(
      type = TrancheType.SENTENCE_LENGTH,
      date = LocalDate.now(),
      duration = null,
      unit = ChronoUnit.DAYS,
      name = TrancheName.TRANCHE_1,
    )
    val durations = listOf(365L, 2000L)
    assertThat(strategy.sentenceDurationsWithinTrancheDuration(trancheConfig, durations)).isFalse
  }

  companion object {
    @JvmStatic
    fun allSdsStrategies(): Stream<Arguments> = Stream.of(
      Arguments.of(SDS40TrancheSelectionStrategy()),
      Arguments.of(SDSProgressionModelTrancheSelectionStrategy()),
    )
  }
}
