package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Period

@ConfigurationProperties("ersed")
data class ErsedConfiguration(
  val maxPeriod: Period,
  val releasePoint: Double,
)
