package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.EarlyReleaseConfiguration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.EarlyReleaseConfigurations
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.EarlyReleaseTrancheConfiguration
import java.time.LocalDate

enum class SDSEarlyReleaseTranche {
  TRANCHE_0,
  TRANCHE_1,
  TRANCHE_2;

  companion object {
    fun fromDate(
      date: LocalDate?,
      earlyReleaseConfigurations: EarlyReleaseConfigurations
    ): SDSEarlyReleaseTranche {
      return earlyReleaseConfigurations.configurations.flatMap { it.tranches }.find { it.date == date }?.name ?: TRANCHE_0
    }
  }
}
