package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "ftr-legislations")
data class FTRLegislationConfiguration(
  val ftr56Legislation: FTRLegislation.FTR56Legislation,
)
