package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("hdced")
data class HdcedConfiguration(
  val minimumDaysOnHdc: Long,
  val minimumCustodialPeriodDays: Long,
  val custodialPeriodMidPointDaysPreHdc365: Long,
  val custodialPeriodAboveMidpointDeductionDaysPreHdc365: Long,
  val custodialPeriodBelowMidpointMinimumDeductionDays: Long,
  val custodialPeriodMidPointDaysPostHdc365: Long,
  val custodialPeriodAboveMidpointDeductionDaysPostHdc365: Long,
)
