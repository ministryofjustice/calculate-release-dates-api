package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("ersed")
data class ErsedConfiguration(
  val maxPeriodDays: Int,
  val releasePoint: Double,
)
