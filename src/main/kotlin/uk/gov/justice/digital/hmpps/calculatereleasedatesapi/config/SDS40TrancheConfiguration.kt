package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.Name
import org.springframework.format.annotation.DateTimeFormat
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.TrancheConfiguration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.TrancheType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.TrancheName
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@ConfigurationProperties("sds-40-early-release-tranches")
data class SDS40TrancheConfiguration(
  @Name("tranche-one-date")
  @DateTimeFormat(pattern = "yyyy-MM-dd") val trancheOneCommencementDate: LocalDate,
  @Name("tranche-two-date")
  @DateTimeFormat(pattern = "yyyy-MM-dd") val trancheTwoCommencementDate: LocalDate,
  @Name("tranche-three-date")
  @DateTimeFormat(pattern = "yyyy-MM-dd") val trancheThreeCommencementDate: LocalDate,
) {
  fun getSds40Tranches() = listOf(
    TrancheConfiguration(
      type = TrancheType.SENTENCE_LENGTH,
      date = trancheOneCommencementDate,
      duration = 5,
      unit = ChronoUnit.YEARS,
      name = TrancheName.TRANCHE_1,
    ),
    TrancheConfiguration(
      type = TrancheType.FINAL,
      date = trancheTwoCommencementDate,
      name = TrancheName.TRANCHE_2,
    ),
  )
}
