package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.EarlyReleaseConfigurations
import java.time.LocalDate

enum class SDSEarlyReleaseTranche {
  TRANCHE_0,
  TRANCHE_1,
  TRANCHE_2,

  FTR_56_TRANCHE_1,
  FTR_56_TRANCHE_2,
  FTR_56_TRANCHE_3,
  FTR_56_TRANCHE_4,
  FTR_56_TRANCHE_5,
  FTR_56_TRANCHE_6,
  ;

  companion object {
    fun fromDate(
      date: LocalDate?,
      earlyReleaseConfigurations: EarlyReleaseConfigurations,
    ): SDSEarlyReleaseTranche = earlyReleaseConfigurations.configurations.flatMap { it.tranches }.find { it.date == date }?.name ?: TRANCHE_0
  }
}
