package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.NestedConfigurationProperty

@ConfigurationProperties("hdced4")
data class Hdced4Configuration(
  @NestedConfigurationProperty
  val envelopeMinimum: ConfiguredPeriod,
  @NestedConfigurationProperty
  val envelopeMidPoint: ConfiguredPeriod,
  val minimumCustodialPeriodDays: Long,
  val deductionDays: Long,
)
