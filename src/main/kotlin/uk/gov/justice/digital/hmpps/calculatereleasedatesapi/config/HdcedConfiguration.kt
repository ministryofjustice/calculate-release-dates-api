package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("hdced")
data class HdcedConfiguration(
  val minimumDaysOnHdc: Long,
  val minimumCustodialPeriodDays: Long,
  val custodialPeriodMidPointDays: Long,
  val custodialPeriodAboveMidpointDeductionDays: Long,
  val custodialPeriodBelowMidpointMinimumDeductionDays: Long,
)
