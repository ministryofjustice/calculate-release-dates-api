package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.math.BigDecimal

class ReleaseMultiplierTest {

  @ParameterizedTest
  @EnumSource(value = ReleaseMultiplier::class, names = ["FULL"], mode = EnumSource.Mode.EXCLUDE)
  fun `check that all release multipliers can apply to a number of days`(releaseMultiplier: ReleaseMultiplier) {
    assertThat(BigDecimal.TEN.multiply(releaseMultiplier.value)).isLessThan(BigDecimal.TEN)
  }
}
