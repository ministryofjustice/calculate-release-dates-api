package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.Name
import org.springframework.format.annotation.DateTimeFormat
import java.time.LocalDate

@ConfigurationProperties("sds-early-release-tranches")
data class SDS40TrancheConfiguration(
  @Name("tranche-one-date")
  @DateTimeFormat(pattern = "yyyy-MM-dd") val trancheOneCommencementDate: LocalDate,
  @Name("tranche-two-date")
  @DateTimeFormat(pattern = "yyyy-MM-dd") val trancheTwoCommencementDate: LocalDate,
  @Name("tranche-three-date")
  @DateTimeFormat(pattern = "yyyy-MM-dd") val trancheThreeCommencementDate: LocalDate,
)
