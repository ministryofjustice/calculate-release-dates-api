package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config

sealed class SDSLegislation(open val configuration: EarlyReleaseConfiguration) {

  data class SDS40Legislation(override val configuration: EarlyReleaseConfiguration) : SDSLegislation(configuration)

  data class ProgressionModelLegislation(override val configuration: EarlyReleaseConfiguration) : SDSLegislation(configuration)
}
