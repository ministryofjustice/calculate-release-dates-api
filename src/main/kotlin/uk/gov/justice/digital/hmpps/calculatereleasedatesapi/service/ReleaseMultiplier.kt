package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import java.math.BigDecimal
import java.math.RoundingMode

enum class ReleaseMultiplier(val value: BigDecimal) {
  FULL(BigDecimal.ONE),
  THREE_QUARTERS(BigDecimal("0.75")),
  TWO_THIRDS(BigDecimal("0.66666666666666666666")),
  ONE_HALF(BigDecimal("0.5")),
  ONE_THIRD(BigDecimal("0.33333333333333333333")),
  FORTY_PERCENT(BigDecimal("0.4")),
  FORTY_THREE_PERCENT(BigDecimal("0.43")),
  FORTY_FIVE_PERCENT(BigDecimal("0.45")),
  THIRTY_PERCENT(BigDecimal("0.3")),
  ;

  fun applyTo(base: BigDecimal, roundingMode: RoundingMode = RoundingMode.CEILING): Long = base.multiply(this.value).toLongReleaseDays(roundingMode)

  fun applyTo(base: Long, roundingMode: RoundingMode = RoundingMode.CEILING): Long = this.applyTo(BigDecimal.valueOf(base), roundingMode)

  fun applyTo(base: Int, roundingMode: RoundingMode = RoundingMode.CEILING): Long = this.applyTo(base.toLong(), roundingMode)

  companion object {
    fun BigDecimal.toLongReleaseDays(roundingMode: RoundingMode = RoundingMode.CEILING): Long = setScale(0, roundingMode).toLong()
    fun BigDecimal.toIntReleaseDays(roundingMode: RoundingMode = RoundingMode.CEILING): Int = setScale(0, roundingMode).toInt()
  }
}
