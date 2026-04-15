package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.TrancheName
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class SDSTrancheSelectionStrategyTest {

  @Nested
  inner class SentenceDurationsWithinTrancheDuration {
    @Test
    fun `should return true if all durations are less than the tranche duration`() {
      val trancheConfig = TrancheConfiguration(
        type = TrancheType.SENTENCE_LENGTH,
        date = LocalDate.now(),
        duration = 4 * 365,
        unit = ChronoUnit.DAYS,
        name = TrancheName.TRANCHE_1,
      )
      val durations = listOf(365L, 730L)
      assertThat(SDSTrancheSelectionStrategy.sentenceDurationsWithinTrancheDuration(trancheConfig, durations)).isTrue
    }

    @Test
    fun `should return false if any duration is equal to the tranche duration`() {
      val trancheConfig = TrancheConfiguration(
        type = TrancheType.SENTENCE_LENGTH,
        date = LocalDate.now(),
        duration = 4 * 365,
        unit = ChronoUnit.DAYS,
        name = TrancheName.TRANCHE_1,
      )
      val durations = listOf(365L, 1460L)
      assertThat(SDSTrancheSelectionStrategy.sentenceDurationsWithinTrancheDuration(trancheConfig, durations)).isFalse
    }

    @Test
    fun `should return false if any duration is greater than the tranche duration`() {
      val trancheConfig = TrancheConfiguration(
        type = TrancheType.SENTENCE_LENGTH,
        date = LocalDate.now(),
        duration = 4 * 365,
        unit = ChronoUnit.DAYS,
        name = TrancheName.TRANCHE_1,
      )
      val durations = listOf(365L, 2000L)
      assertThat(SDSTrancheSelectionStrategy.sentenceDurationsWithinTrancheDuration(trancheConfig, durations)).isFalse
    }

    @Test
    fun `should return false if tranche duration is null`() {
      val trancheConfig = TrancheConfiguration(
        type = TrancheType.SENTENCE_LENGTH,
        date = LocalDate.now(),
        duration = null,
        unit = ChronoUnit.DAYS,
        name = TrancheName.TRANCHE_1,
      )
      val durations = listOf(365L, 2000L)
      assertThat(SDSTrancheSelectionStrategy.sentenceDurationsWithinTrancheDuration(trancheConfig, durations)).isFalse
    }
  }
}
