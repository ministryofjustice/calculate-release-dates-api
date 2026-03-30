package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config

class ApplicableSDSLegislations {
  private val applicableLegislations: MutableMap<LegislationName, ApplicableLegislation<SDSLegislation>> = mutableMapOf()

  fun setApplicableLegislation(applicableLegislation: ApplicableLegislation<SDSLegislation>) {
    applicableLegislations[applicableLegislation.legislation.legislationName] = applicableLegislation
  }

  fun getApplicableLegislation(legislationName: LegislationName): ApplicableLegislation<SDSLegislation>? = applicableLegislations[legislationName]

  fun legislationByCommencementDate(): Set<ApplicableLegislation<SDSLegislation>> = applicableLegislations.values.toSortedSet(compareByDescending<ApplicableLegislation<SDSLegislation>> { it.legislation.commencementDate() }.thenBy { it.legislation.legislationName })

  fun hasTrancheSet(legislationName: LegislationName): Boolean = applicableLegislations[legislationName]?.earliestApplicableDate != null
}
