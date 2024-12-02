package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.manageoffencesapi

data class SDSEarlyReleaseExclusionForOffenceCode(val offenceCode: String, val schedulePart: SDSEarlyReleaseExclusionSchedulePart)
enum class SDSEarlyReleaseExclusionSchedulePart {
  SEXUAL,
  VIOLENT,
  DOMESTIC_ABUSE,
  NATIONAL_SECURITY,
  TERRORISM,
  SEXUAL_T3,
  VIOLENT_T3,
  DOMESTIC_ABUSE_T3,
  NATIONAL_SECURITY_T3,
  TERRORISM_T3,
  MURDER,
  MURDER_T3,
  NONE,
}
