package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.format.annotation.DateTimeFormat
import java.time.LocalDate

@ConfigurationProperties("sds-early-release-tranches")
data class SDS40TrancheConfiguration(
  @Value("\${sds-early-release-tranches.tranche-one-date}")
  @DateTimeFormat(pattern = "yyyy-MM-dd") val trancheOneCommencementDate: LocalDate,
  @Value("\${sds-early-release-tranches.tranche-two-date}")
  @DateTimeFormat(pattern = "yyyy-MM-dd") val trancheTwoCommencementDate: LocalDate,
  )
