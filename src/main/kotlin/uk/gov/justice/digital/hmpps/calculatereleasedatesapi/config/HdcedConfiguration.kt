package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.NestedConfigurationProperty

@ConfigurationProperties("hdced")
data class HdcedConfiguration(
  @NestedConfigurationProperty
  val envelopeMinimum: ConfiguredPeriod,
  @NestedConfigurationProperty
  val envelopeMaximum: ConfiguredPeriod,
  @NestedConfigurationProperty
  val envelopeMidPoint: ConfiguredPeriod,
  val minimumCustodialPeriodDays: Long,
  val deductionDays: Long,
)
