package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "sds-legislations")
data class SDSLegislationConfiguration(
  val defaultLegislation: SDSLegislation.DefaultSDSLegislation,
  val sds40Legislation: SDSLegislation.SDS40Legislation,
  val sds40AdditionalExcludedOffencesLegislation: SDSLegislation.SDS40AdditionalExcludedOffencesLegislation,
  val progressionModelLegislation: SDSLegislation.ProgressionModelLegislation?,
) {
  fun all(): List<SDSLegislation> = listOfNotNull(
    defaultLegislation,
    sds40Legislation,
    sds40AdditionalExcludedOffencesLegislation,
    progressionModelLegislation,
  )
}
