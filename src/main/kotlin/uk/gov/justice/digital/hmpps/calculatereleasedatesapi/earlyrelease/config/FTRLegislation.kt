package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config

sealed class FTRLegislation(open val configuration: EarlyReleaseConfiguration) {
  data class FTR56Legislation(override val configuration: EarlyReleaseConfiguration) : FTRLegislation(configuration)
}
