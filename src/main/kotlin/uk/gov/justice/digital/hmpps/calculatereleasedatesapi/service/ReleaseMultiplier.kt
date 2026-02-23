package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import java.math.BigDecimal
import java.math.RoundingMode

enum class ReleaseMultiplier(val value: BigDecimal) {
  FULL(BigDecimal.ONE),
  TWO_THIRDS(BigDecimal("0.66666666666666666666")),
  ONE_HALF(BigDecimal("0.5")),
  ONE_THIRD(BigDecimal("0.33333333333333333333")),
  FORTY_PERCENT(BigDecimal("0.4")),
  FORTY_THREE_PERCENT(BigDecimal("0.43")),
  FORTY_FIVE_PERCENT(BigDecimal("0.45")),
  ;

  companion object {
    fun BigDecimal.toLongReleaseDays(): Long = setScale(0, RoundingMode.CEILING).toLong()
    fun BigDecimal.toIntReleaseDays(): Int = setScale(0, RoundingMode.CEILING).toInt()
  }
}
