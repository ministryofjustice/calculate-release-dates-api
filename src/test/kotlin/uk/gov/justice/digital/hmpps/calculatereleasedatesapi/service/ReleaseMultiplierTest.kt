package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.math.BigDecimal
import java.math.RoundingMode

class ReleaseMultiplierTest {

  @ParameterizedTest
  @EnumSource(value = ReleaseMultiplier::class, names = ["FULL"], mode = EnumSource.Mode.EXCLUDE)
  fun `check that all release multipliers can apply to a number of days`(releaseMultiplier: ReleaseMultiplier) {
    assertThat(BigDecimal.TEN.multiply(releaseMultiplier.value)).isLessThan(BigDecimal.TEN)
  }

  @Test
  fun `apply to rounds using ceiling by default`() {
    assertThat(ReleaseMultiplier.ONE_HALF.applyTo(BigDecimal("5"))).isEqualTo(3)
    assertThat(ReleaseMultiplier.ONE_HALF.applyTo(5L)).isEqualTo(3)
    assertThat(ReleaseMultiplier.ONE_HALF.applyTo(5)).isEqualTo(3)
  }

  @Test
  fun `apply to rounds using specified rounding method`() {
    assertThat(ReleaseMultiplier.ONE_HALF.applyTo(BigDecimal("5"), RoundingMode.FLOOR)).isEqualTo(2)
    assertThat(ReleaseMultiplier.ONE_HALF.applyTo(5L, RoundingMode.FLOOR)).isEqualTo(2)
    assertThat(ReleaseMultiplier.ONE_HALF.applyTo(5, RoundingMode.FLOOR)).isEqualTo(2)
  }
}
