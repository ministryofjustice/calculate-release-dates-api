package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("release-point-multipliers")
data class ReleasePointMultipliersConfiguration(val earlyReleasePoint: Double)
