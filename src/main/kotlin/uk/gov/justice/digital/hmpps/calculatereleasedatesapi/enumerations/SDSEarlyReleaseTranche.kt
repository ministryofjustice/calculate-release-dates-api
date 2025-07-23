package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.EarlyReleaseConfigurations
import java.time.LocalDate

enum class SDSEarlyReleaseTranche {
  TRANCHE_0,
  TRANCHE_1,
  TRANCHE_2,
  ;

  companion object {
    fun fromDate(
      date: LocalDate?,
      earlyReleaseConfigurations: EarlyReleaseConfigurations,
    ): SDSEarlyReleaseTranche = earlyReleaseConfigurations.configurations.flatMap { it.tranches }.find { it.date == date }?.name ?: TRANCHE_0
  }
}
