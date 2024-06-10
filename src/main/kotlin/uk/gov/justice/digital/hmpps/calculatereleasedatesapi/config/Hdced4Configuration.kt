package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config

import org.joda.time.DateTime
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.NestedConfigurationProperty
import org.springframework.format.annotation.DateTimeFormat
import java.util.*

@ConfigurationProperties("hdced4")
data class Hdced4Configuration(
  @NestedConfigurationProperty
  val envelopeMinimum: ConfiguredPeriod,
  @NestedConfigurationProperty
  val envelopeMidPoint: ConfiguredPeriod,
  val minimumCustodialPeriodDays: Long,
  val deductionDays: Long,
  @DateTimeFormat(pattern = "yyyy-MM-dd")
  val hdc4CommencementDate: Date = DateTime.now().plusDays(1).toDate(),
)
